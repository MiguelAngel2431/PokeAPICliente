package com.digis01.PokeAPICliente.Service;

import com.digis01.PokeAPICliente.DTO.*;
import com.digis01.PokeAPICliente.ML.Result;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PokedexCacheService {

    /* ========================= CONFIG ========================= */
    private static final int THREADS      = Math.max(8, Runtime.getRuntime().availableProcessors() * 2);
    private static final int PAGE_FETCH   = 200;
    private static final int MAX_RETRIES  = 5;
    private static final int MAX_INFLIGHT = 32; // rate-limit para no saturar la API

    private static final Path CACHE_FILE  = Path.of("cache", "pokedex.json");    // snapshot ligero
    private static final Path FULL_DIR    = Path.of("cache", "full");            // dump completo NDJSON
    private static final Path CATALOG_IDX = FULL_DIR.resolve("catalog-index.json");
    private static final long MAX_AGE_SECONDS = 60L * 60L * 3L; // 3 horas fresco

    /* ========================= INFRA ========================== */
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper json = new ObjectMapper()
            .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, true);
    private final ExecutorService pool = new ThreadPoolExecutor(
            THREADS, THREADS, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
            r -> { Thread t = new Thread(r, "poke-loader"); t.setDaemon(true); return t; });
    private final Semaphore inflight = new Semaphore(MAX_INFLIGHT);

    /* ========================= ESTADO ========================= */
    private final AtomicBoolean warming = new AtomicBoolean(false);
    private final AtomicInteger progress = new AtomicInteger(0);
    private final AtomicInteger goal = new AtomicInteger(0);
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private volatile Instant lastUpdated = null;

    // caches en memoria
    private final Map<Integer, PokemonFullDTO> byId = new ConcurrentHashMap<>();
    private final List<PokemonCardDTO> cards = new CopyOnWriteArrayList<>();
    private final Set<String> allTypes = ConcurrentHashMap.newKeySet();

    // breakdown de progreso por recurso (para /status)
    private final Map<String, Map<String, Integer>> resourcesStatus = new ConcurrentHashMap<>();

    @PostConstruct
    public void tryLoadCacheFromDiskOnBoot() {
        try {
            Files.createDirectories(CACHE_FILE.getParent());
            Files.createDirectories(FULL_DIR);
            loadDiskCacheIfExists();
            if (cards.isEmpty()) warmupAsync(false);
        } catch (Exception e) {
            lastError.set("Cache boot error: " + e.getMessage());
        }
    }

    /* ========================= API PÚBLICA sssssssss==================== */

    /** Dispara la descarga completa + reconstrucción del snapshot ligero. */
    public Result<Void> warmupAsync(boolean force) {
        if (warming.get() && !force) return Result.loading(currentProgress());
        if (!force && isCacheFresh()) return Result.ok(null);
        if (warming.compareAndSet(false, true)) {
            lastError.set(null); progress.set(0); goal.set(1);
            pool.submit(() -> {
                try {
                    fullHarvest();                  // cache/full/*.ndjson
                    rebuildLightPokedexFromFullDump(); // pokedex.json + memoria
                    lastUpdated = Instant.now();
                } catch (Exception e) {
                    lastError.set("Warmup failed: " + e.getMessage());
                } finally {
                    warming.set(false);
                }
            });
            return Result.loading(0);
        }
        return Result.loading(currentProgress());
    }

    /** Página de cards (igual que tu index usa). */
    public Result<PageDTO<PokemonCardDTO>> getIndexPage(int page, int size) {
        if (warming.get() && cards.isEmpty()) return Result.loading(currentProgress());
        if (cards.isEmpty()) { warmupAsync(false); return Result.loading(currentProgress()); }
        return paginate(cards, page, size);
    }

    /** Búsqueda simple por nombre. */
    public Result<PageDTO<PokemonCardDTO>> searchCards(String q, int page, int size) {
        if (warming.get() && cards.isEmpty()) return Result.loading(currentProgress());
        if (cards.isEmpty()) { warmupAsync(false); return Result.loading(currentProgress()); }

        String term = (q == null ? "" : q.trim().toLowerCase());
        List<PokemonCardDTO> base = cards;
        if (!term.isEmpty()) {
            base = cards.stream()
                    .filter(c -> c.name != null && c.name.toLowerCase().contains(term))
                    .collect(Collectors.toList());
        }
        return paginate(base, page, size);
    }

    /** Tipos presentes en el catálogo (para filtros). */
    public Set<String> allTypes() {
        return new TreeSet<>(allTypes);
    }

    /** Todas las cards en memoria. */
    public List<PokemonCardDTO> allCards() {
        return new ArrayList<>(cards);
    }

    /** Detalle por id (si no está, dispara warmup y devuelve loading). */
    public Result<PokemonFullDTO> getById(int id) {
        PokemonFullDTO p = byId.get(id);
        if (p != null) return Result.ok(p);
        warmupAsync(false);
        return Result.loading(currentProgress());
    }

    /** Estado para la pantalla de carga. */
    public Map<String, Object> status() {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("warming", warming.get());
        m.put("progress", currentProgress());
        m.put("goal", goal.get());
        m.put("count", cards.size());
        m.put("lastUpdated", lastUpdated);
        m.put("error", lastError.get());
        m.put("resources", snapshotResourcesStatus());
        return m;
    }

    /* ========================= FULL HARVEST ==================== */

    // Orden de recursos a volcar (además de pokemon + pokemon-form que ensamblamos)
    private static final List<String> RESOURCE_ORDER = List.of(
            "pokemon",               // base (detalles)
            "pokemon-form",          // usamos para deducir urls de pokemon
            "pokemon-species",
            "ability",
            "move",
            "type",
            "item",
            "evolution-chain",
            "generation",
            "version-group",
            "version",
            "machine",
            "stat",
            "location",
            "location-area",
            "encounter-method",
            "encounter-condition",
            "encounter-condition-value"
    );

    /** Descarga todo el catálogo a NDJSON, con reintentos y segunda pasada. */
    private void fullHarvest() {
        resourcesStatus.clear();
        FilesX.ensureDir(FULL_DIR);

        // 1) Recolecta detail-urls para /pokemon (union /pokemon + /pokemon-form→pokemon.url)
        List<String> pokemonDetailUrls = collectAllPokemonDetailUrls();
        updateResourceGoal("pokemon", pokemonDetailUrls.size());
        goal.set(Math.max(pokemonDetailUrls.size(), 1));

        // 2) Descarga detalle de POKEMON (debe llegar a ~1328)
        Path pokemonOut = FULL_DIR.resolve("pokemon.ndjson");
        writeNdjson(pokemonOut, pokemonDetailUrls, url -> getJsonWithRetry(url, MAX_RETRIES), "pokemon");

        // 3) Resto de recursos: pagina listado y baja cada detalle
        for (String resource : RESOURCE_ORDER) {
            if (resource.equals("pokemon") || resource.equals("pokemon-form")) continue; // pokemon ya hecho; forms solo se usan para deducir urls
            String base = "https://pokeapi.co/api/v2/" + resource;
            List<Map<String,Object>> results = fetchAllPaged(base, 500, MAX_RETRIES);
            List<String> detailUrls = results.stream()
                    .map(m -> String.valueOf(m.get("url")))
                    .filter(u -> u != null && !u.isBlank())
                    .toList();

            updateResourceGoal(resource, detailUrls.size());
            goal.addAndGet(detailUrls.size());
            Path out = FULL_DIR.resolve(resource.replace('-', '_') + ".ndjson");
            writeNdjson(out, detailUrls, url -> getJsonWithRetry(url, MAX_RETRIES), resource);
        }

        // 4) Índice de conteos por archivo
        writeCatalogIndex();
    }

    /** Escribe NDJSON con concurrencia; hace segunda pasada a fallidas. */
    private void writeNdjson(Path file, List<String> detailUrls,
                             Function<String, Map<String,Object>> fetcher,
                             String resourceKey) {
        final Object lock = new Object();
        final List<String> failed = Collections.synchronizedList(new ArrayList<>());

        try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(file))) {
            List<CompletableFuture<Void>> futs = new ArrayList<>(detailUrls.size());
            for (String u : detailUrls) {
                CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {
                    try {
                        inflight.acquire();
                        Map<String,Object> detail = fetcher.apply(u);
                        if (detail != null) {
                            byte[] line = (json.writeValueAsString(detail) + "\n").getBytes();
                            synchronized (lock) { os.write(line); }
                        } else {
                            failed.add(u);
                        }
                    } catch (InterruptedException | IOException e) {
                        failed.add(u);
                    } finally {
                        inflight.release();
                        incrementResourceDone(resourceKey);
                        progress.incrementAndGet();
                    }
                }, pool);
                futs.add(f);
            }
            for (CompletableFuture<Void> f : futs) { try { f.get(); } catch (Exception ignored) {} }

            // Segunda pasada serial con más retries (recupera caídas puntuales/429)
            if (!failed.isEmpty()) {
                List<String> second = new ArrayList<>(failed);
                failed.clear();
                for (String u : second) {
                    try {
                        Map<String,Object> detail = getJsonWithRetry(u, MAX_RETRIES + 3);
                        if (detail != null) {
                            byte[] line = (json.writeValueAsString(detail) + "\n").getBytes();
                            synchronized (lock) { os.write(line); }
                        }
                    } catch (IOException ignored) {}
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Une /pokemon y /pokemon-form→pokemon.url, dedupe y devuelve detail-urls finales. */
    @SuppressWarnings("unchecked")
    private List<String> collectAllPokemonDetailUrls() {
        // /pokemon (intenta mega-limit, si no funciona paginate)
        Map<String,Object> root = getJsonWithRetry("https://pokeapi.co/api/v2/pokemon?limit=200000&offset=0", MAX_RETRIES);
        List<Map<String,Object>> pokemonResults = (root != null)
                ? (List<Map<String,Object>>) root.getOrDefault("results", Collections.emptyList())
                : Collections.emptyList();
        if (pokemonResults.isEmpty()) {
            pokemonResults = fetchAllPaged("https://pokeapi.co/api/v2/pokemon", PAGE_FETCH, MAX_RETRIES);
        }
        List<String> fromPokemon = pokemonResults.stream()
                .map(m -> String.valueOf(m.get("url")))
                .filter(u -> u != null && !u.isBlank())
                .toList();

        // /pokemon-form (cada form tiene { pokemon: {url} })
        List<Map<String,Object>> forms = fetchAllPaged("https://pokeapi.co/api/v2/pokemon-form", 500, MAX_RETRIES);
        List<CompletableFuture<String>> futs = new ArrayList<>(forms.size());
        for (Map<String,Object> fr : forms) {
            Object u = fr.get("url");
            if (u == null) continue;
            String formDetailUrl = String.valueOf(u);
            CompletableFuture<String> f = CompletableFuture.supplyAsync(() -> {
                try {
                    inflight.acquire();
                    Map<String,Object> form = getJsonWithRetry(formDetailUrl, MAX_RETRIES);
                    if (form == null) return null;
                    Map<String,Object> pk = castMap(form.get("pokemon"));
                    if (pk == null) return null;
                    Object pkUrl = pk.get("url");
                    return (pkUrl == null) ? null : pkUrl.toString();
                } catch (InterruptedException ignored) {
                    return null;
                } finally {
                    inflight.release();
                }
            }, pool);
            futs.add(f);
        }
        List<String> fromForms = futs.stream().map(f -> {
            try { return f.get(); } catch (Exception e) { return null; }
        }).filter(Objects::nonNull).toList();

        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.addAll(fromPokemon);
        set.addAll(fromForms);
        return new ArrayList<>(set);
    }

    /* ================== REBUILD pokedex.json (tus DTOs) ================== */

    /** Reconstruye el snapshot ligero y llena memoria. */
    private void rebuildLightPokedexFromFullDump() {
        Path pokemonNd = FULL_DIR.resolve("pokemon.ndjson");
        if (!Files.exists(pokemonNd)) return;

        byId.clear();
        cards.clear();
        allTypes.clear();

        try (var lines = Files.lines(pokemonNd)) {
            lines.forEach(line -> {
                if (line == null || line.isBlank()) return;
                Map<String,Object> d = jsonSafe(line);
                PokemonFullDTO pf = mapToFullDTO(d);
                if (pf == null) return;
                byId.put(pf.id, pf);
                cards.add(toCard(pf));
                if (pf.types != null) allTypes.addAll(pf.types);
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<PokemonFullDTO> all = new ArrayList<>(byId.values());
        all.sort(Comparator.comparingInt(p -> p.id));
        persistToDisk(all);
    }

    /* ========================= MAPEOS ========================= */

    private PokemonCardDTO toCard(PokemonFullDTO pf) {
        PokemonCardDTO c = new PokemonCardDTO();
        c.id = pf.id;
        c.name = pf.name;
        c.image = pf.image;
        c.types = pf.types;
        c.stats = pf.stats;
        c.heightM = pf.heightM;
        c.weightKg = pf.weightKg;
        c.baseExp = pf.baseExp;
        return c;
    }

    @SuppressWarnings("unchecked")
    private PokemonFullDTO mapToFullDTO(Map<String,Object> d) {
        if (d == null) return null;
        PokemonFullDTO p = new PokemonFullDTO();

        // básicos
        p.id    = ((Number) d.getOrDefault("id", 0)).intValue();
        p.name  = String.valueOf(d.getOrDefault("name", "pokemon"));
        p.image = extractPrimaryImage(d);

        // flags y orden
        Object isDef = d.get("is_default");
        p.isDefault = (isDef instanceof Boolean) ? (Boolean) isDef : Boolean.TRUE.equals(isDef);
        if (d.get("order") instanceof Number n) p.order = n.intValue();

        // location encounters
        p.locationAreaEncounters = String.valueOf(d.getOrDefault("location_area_encounters", ""));

        // species (resource)
        Map<String,Object> species = castMap(d.get("species"));
        if (species != null) {
            NamedAPIResource r = new NamedAPIResource();
            r.name = String.valueOf(species.get("name"));
            r.url  = String.valueOf(species.get("url"));
            p.species = r;
        }

        // forms[]
        List<Map<String,Object>> forms = (List<Map<String,Object>>) d.get("forms");
        if (forms != null) {
            List<NamedAPIResource> ff = new ArrayList<>();
            for (Map<String,Object> fm : forms) {
                NamedAPIResource r = new NamedAPIResource();
                r.name = String.valueOf(fm.get("name"));
                r.url  = String.valueOf(fm.get("url"));
                ff.add(r);
            }
            p.forms = ff;
        }

        // types simples (para filtros)
        List<String> types = new ArrayList<>();
        List<Map<String,Object>> typesObj = (List<Map<String,Object>>) d.get("types");
        if (typesObj != null) {
            for (Map<String,Object> t : typesObj) {
                Map<String,Object> tm = castMap(t.get("type"));
                if (tm != null && tm.get("name") != null) types.add(String.valueOf(tm.get("name")));
            }
        }
        p.types = types;

        // rawTypes (slot + NamedAPIResource)
        if (typesObj != null) {
            List<TypeSlotDTO> rts = new ArrayList<>();
            for (Map<String,Object> t : typesObj) {
                TypeSlotDTO ts = new TypeSlotDTO();
                Object slot = t.get("slot");
                ts.slot = (slot instanceof Number) ? ((Number) slot).intValue() : 0;
                Map<String,Object> tm = castMap(t.get("type"));
                if (tm != null) {
                    NamedAPIResource r = new NamedAPIResource();
                    r.name = String.valueOf(tm.get("name"));
                    r.url  = String.valueOf(tm.get("url"));
                    ts.type = r;
                }
                rts.add(ts);
            }
            p.rawTypes = rts;
        }

        // stats agregados + rawStats
        Map<String,Integer> stats = new LinkedHashMap<>();
        stats.put("hp", 0); stats.put("attack", 0); stats.put("defense", 0); stats.put("speed", 0);
        List<Map<String,Object>> statsObj = (List<Map<String,Object>>) d.get("stats");
        if (statsObj != null) {
            List<StatEntryDTO> raw = new ArrayList<>();
            for (Map<String,Object> s : statsObj) {
                int base = ((Number) s.getOrDefault("base_stat", 0)).intValue();
                int eff  = ((Number) s.getOrDefault("effort", 0)).intValue();

                Map<String,Object> st = castMap(s.get("stat"));
                String n = (st != null) ? String.valueOf(st.get("name")) : "";

                switch (n) {
                    case "hp" -> stats.put("hp", base);
                    case "attack" -> stats.put("attack", base);
                    case "defense" -> stats.put("defense", base);
                    case "special-attack" -> p.specialAttack = base;
                    case "special-defense" -> p.specialDefense = base;
                    case "speed" -> stats.put("speed", base);
                }

                StatEntryDTO se = new StatEntryDTO();
                se.baseStat = base;
                se.effort = eff;
                if (st != null) {
                    NamedAPIResource r = new NamedAPIResource();
                    r.name = String.valueOf(st.get("name"));
                    r.url  = String.valueOf(st.get("url"));
                    se.stat = r;
                }
                raw.add(se);
            }
            p.rawStats = raw;
        }
        p.stats = stats;

        // medidas y exp
        p.heightM = ((Number) d.getOrDefault("height", 0)).doubleValue() / 10.0;
        p.weightKg = ((Number) d.getOrDefault("weight", 0)).doubleValue() / 10.0;
        if (d.get("base_experience") instanceof Number bx) p.baseExp = bx.intValue();

        // sprites (incluye showdown)
        p.sprites = extractAllSprites(d);

        // abilities (name, hidden, url, slot)
        List<AbilityDTO> abilities = new ArrayList<>();
        List<Map<String,Object>> abObj = (List<Map<String,Object>>) d.get("abilities");
        if (abObj != null) {
            for (Map<String,Object> a : abObj) {
                AbilityDTO ab = new AbilityDTO();
                Map<String,Object> ability = castMap(a.get("ability"));
                if (ability != null) {
                    ab.name = String.valueOf(ability.get("name"));
                    ab.url  = String.valueOf(ability.get("url"));
                }
                ab.hidden = Boolean.TRUE.equals(a.get("is_hidden"));
                Object slot = a.get("slot");
                ab.slot = (slot instanceof Number) ? ((Number) slot).intValue() : null;
                abilities.add(ab);
            }
        }
        p.abilities = abilities;

        // game_indices
        List<Map<String,Object>> gi = (List<Map<String,Object>>) d.get("game_indices");
        if (gi != null) {
            List<GameIndexDTO> out = new ArrayList<>();
            for (Map<String,Object> g : gi) {
                GameIndexDTO gidx = new GameIndexDTO();
                if (g.get("game_index") instanceof Number n) gidx.gameIndex = n.intValue();
                Map<String,Object> ver = castMap(g.get("version"));
                if (ver != null) {
                    NamedAPIResource r = new NamedAPIResource();
                    r.name = String.valueOf(ver.get("name"));
                    r.url  = String.valueOf(ver.get("url"));
                    gidx.version = r;
                }
                out.add(gidx);
            }
            p.gameIndices = out;
        }

        // held_items
        List<Map<String,Object>> held = (List<Map<String,Object>>) d.get("held_items");
        if (held != null) {
            List<HeldItemDTO> out = new ArrayList<>();
            for (Map<String,Object> hi : held) {
                HeldItemDTO h = new HeldItemDTO();
                Map<String,Object> item = castMap(hi.get("item"));
                if (item != null) {
                    NamedAPIResource r = new NamedAPIResource();
                    r.name = String.valueOf(item.get("name"));
                    r.url  = String.valueOf(item.get("url"));
                    h.item = r;
                }
                List<Map<String,Object>> vds = (List<Map<String,Object>>) hi.get("version_details");
                if (vds != null) {
                    List<HeldItemVersionDetailDTO> list = new ArrayList<>();
                    for (Map<String,Object> vd : vds) {
                        HeldItemVersionDetailDTO v = new HeldItemVersionDetailDTO();
                        Map<String,Object> ver = castMap(vd.get("version"));
                        if (ver != null) {
                            NamedAPIResource r = new NamedAPIResource();
                            r.name = String.valueOf(ver.get("name"));
                            r.url  = String.valueOf(ver.get("url"));
                            v.version = r;
                        }
                        if (vd.get("rarity") instanceof Number rn) v.rarity = rn.intValue();
                        list.add(v);
                    }
                    h.versionDetails = list;
                }
                out.add(h);
            }
            p.heldItems = out;
        }

        // moves
        List<Map<String,Object>> moves = (List<Map<String,Object>>) d.get("moves");
        if (moves != null) {
            List<MoveDTO> out = new ArrayList<>();
            for (Map<String,Object> mv : moves) {
                MoveDTO md = new MoveDTO();

                Map<String,Object> moveRef = castMap(mv.get("move"));
                if (moveRef != null) {
                    NamedAPIResource r = new NamedAPIResource();
                    r.name = String.valueOf(moveRef.get("name"));
                    r.url  = String.valueOf(moveRef.get("url"));
                    md.move = r;
                }

                List<Map<String,Object>> vgs = (List<Map<String,Object>>) mv.get("version_group_details");
                if (vgs != null) {
                    List<MoveVersionDetailDTO> list = new ArrayList<>();
                    for (Map<String,Object> det : vgs) {
                        MoveVersionDetailDTO mvd = new MoveVersionDetailDTO();
                        Map<String,Object> mlm = castMap(det.get("move_learn_method"));
                        if (mlm != null) {
                            NamedAPIResource r = new NamedAPIResource();
                            r.name = String.valueOf(mlm.get("name"));
                            r.url  = String.valueOf(mlm.get("url"));
                            mvd.moveLearnMethod = r;
                        }
                        Map<String,Object> vg = castMap(det.get("version_group"));
                        if (vg != null) {
                            NamedAPIResource r = new NamedAPIResource();
                            r.name = String.valueOf(vg.get("name"));
                            r.url  = String.valueOf(vg.get("url"));
                            mvd.versionGroup = r;
                        }
                        if (det.get("level_learned_at") instanceof Number ln) mvd.levelLearnedAt = ln.intValue();
                        if (det.get("order") instanceof Number on) mvd.order = on.intValue();
                        list.add(mvd);
                    }
                    md.versionGroupDetails = list;
                }
                out.add(md);
            }
            p.moves = out;
        }

        // cries (latest/legacy) + compat con p.cry
        Map<String,Object> cries = castMap(d.get("cries"));
        if (cries != null) {
            Object latest = cries.get("latest");
            Object legacy = cries.get("legacy");
            p.cryLatest = (latest != null) ? String.valueOf(latest) : null;
            p.cryLegacy = (legacy != null) ? String.valueOf(legacy) : null;
            p.cry = (p.cryLatest != null) ? p.cryLatest : p.cryLegacy;
        }

        // genus + flavor desde species url
        try {
            if (species != null && species.get("url") != null) {
                String sUrl = String.valueOf(species.get("url"));
                Map<String,Object> sp = getJsonWithRetry(sUrl, MAX_RETRIES);
                if (sp != null) {
                    p.genus  = pickLocalized(sp, "genera", "genus");
                    p.flavor = pickLocalized(sp, "flavor_text_entries", "flavor_text");
                }
            }
        } catch (Exception ignored) {}

        return p;
    }

    /* ========================= HELPERS ========================= */

    private Result<PageDTO<PokemonCardDTO>> paginate(List<PokemonCardDTO> list, int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.max(size, 1);
        int total = list.size();
        int from = Math.min(total, (p - 1) * s);
        int to = Math.min(total, from + s);

        PageDTO<PokemonCardDTO> pg = new PageDTO<>();
        pg.page = p;
        pg.size = s;
        pg.total = total;
        pg.totalPages = (int) Math.max(1, Math.ceil(total / (double) s));
        pg.items = (from >= to) ? Collections.emptyList() : list.subList(from, to);
        return Result.ok(pg);
    }

    private boolean isCacheFresh() {
        return lastUpdated != null && Instant.now().minusSeconds(MAX_AGE_SECONDS).isBefore(lastUpdated);
    }

    private int currentProgress() {
        int g = Math.max(1, goal.get());
        int p = Math.min(g, progress.get());
        return Math.round((p * 100f) / g);
    }

    /* ---------- HTTP con retry + 429 ---------- */

    @SuppressWarnings("unchecked")
    private Map<String,Object> getJsonWithRetry(String url, int maxRetries) {
        int attempt = 0;
        long base = 250L; // ms
        while (true) {
            try {
                return http.getForObject(url, Map.class);
            } catch (HttpStatusCodeException httpEx) {
                int code = httpEx.getStatusCode().value();
                attempt++;
                if (code == 429) {
                    long wait = Math.min(8000L, base * (1L << Math.min(5, attempt)));
                    wait += ThreadLocalRandom.current().nextLong(0, 250);
                    try { Thread.sleep(wait); } catch (InterruptedException ignored) {}
                    continue; // no contamos el intento contra el maxRetries
                }
                if (attempt > maxRetries) return null;
                long wait = Math.min(5000L, base * (1L << Math.min(5, attempt))) + ThreadLocalRandom.current().nextLong(0, 200);
                try { Thread.sleep(wait); } catch (InterruptedException ignored) {}
            } catch (Exception ex) {
                attempt++;
                if (attempt > maxRetries) return null;
                long wait = Math.min(5000L, base * (1L << Math.min(5, attempt))) + ThreadLocalRandom.current().nextLong(0, 200);
                try { Thread.sleep(wait); } catch (InterruptedException ignored) {}
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> fetchAllPaged(String baseUrl, int pageSize, int maxRetries) {
        List<Map<String,Object>> acc = new ArrayList<>();
        int offset = 0;
        while (true) {
            String url = baseUrl + "?offset=" + offset + "&limit=" + pageSize;
            Map<String,Object> page = getJsonWithRetry(url, maxRetries);
            if (page == null) break;
            List<Map<String,Object>> rs = (List<Map<String,Object>>) page.getOrDefault("results", Collections.emptyList());
            if (rs.isEmpty()) break;
            acc.addAll(rs);
            Object next = page.get("next");
            if (next == null) break;
            offset += pageSize;
        }
        return acc;
    }

    /* ---------- Status por recurso ---------- */

    private void updateResourceGoal(String key, int g) {
        resourcesStatus.compute(key, (k, v) -> {
            Map<String,Integer> m = (v == null) ? new ConcurrentHashMap<>() : v;
            m.put("goal", g);
            m.putIfAbsent("done", 0);
            return m;
        });
    }
    private void incrementResourceDone(String key) {
        resourcesStatus.compute(key, (k,v) -> {
            Map<String,Integer> m = (v == null) ? new ConcurrentHashMap<>() : v;
            m.putIfAbsent("goal", 0);
            m.put("done", m.getOrDefault("done", 0) + 1);
            return m;
        });
    }
    private Map<String, Map<String, Integer>> snapshotResourcesStatus() {
        Map<String, Map<String, Integer>> snap = new LinkedHashMap<>();
        for (String k : new TreeSet<>(resourcesStatus.keySet())) {
            Map<String,Integer> v = resourcesStatus.get(k);
            if (v == null) continue;
            snap.put(k, Map.of(
                    "goal", v.getOrDefault("goal", 0),
                    "done", v.getOrDefault("done", 0)
            ));
        }
        return snap;
    }

    /* ---------- Sprites / imagen principal / textos ---------- */

    private String extractPrimaryImage(Map<String,Object> d) {
        String image = null;
        try {
            Map<String,Object> sprites = castMap(d.get("sprites"));
            if (sprites != null) {
                Map<String,Object> other = castMap(sprites.get("other"));
                if (other != null) {
                    Map<String,Object> official = castMap(other.get("official-artwork"));
                    if (official != null) image = (String) official.get("front_default");
                    if (image == null) {
                        Map<String,Object> dream = castMap(other.get("dream_world"));
                        if (dream != null) image = (String) dream.get("front_default");
                    }
                }
                if (image == null) image = (String) sprites.get("front_default");
            }
        } catch (ClassCastException ignored) {}
        return image == null ? "" : image;
    }

    private List<SpriteDTO> extractAllSprites(Map<String,Object> d) {
        List<SpriteDTO> out = new ArrayList<>();
        try {
            Map<String,Object> sprites = castMap(d.get("sprites"));
            if (sprites == null) return out;

            addIfUrl(out, "front_default", sprites.get("front_default"));
            addIfUrl(out, "back_default", sprites.get("back_default"));
            addIfUrl(out, "front_shiny", sprites.get("front_shiny"));
            addIfUrl(out, "back_shiny", sprites.get("back_shiny"));
            addIfUrl(out, "front_female", sprites.get("front_female"));
            addIfUrl(out, "back_female", sprites.get("back_female"));
            addIfUrl(out, "front_shiny_female", sprites.get("front_shiny_female"));
            addIfUrl(out, "back_shiny_female", sprites.get("back_shiny_female"));

            Map<String,Object> other = castMap(sprites.get("other"));
            if (other != null) {
                Map<String,Object> home = castMap(other.get("home"));
                if (home != null) {
                    addIfUrl(out, "home", home.get("front_default"));
                    addIfUrl(out, "home_shiny", home.get("front_shiny"));
                }
                Map<String,Object> official = castMap(other.get("official-artwork"));
                if (official != null) {
                    addIfUrl(out, "official", official.get("front_default"));
                    addIfUrl(out, "official_shiny", official.get("front_shiny"));
                }
                Map<String,Object> dream = castMap(other.get("dream_world"));
                if (dream != null) addIfUrl(out, "dream_world", dream.get("front_default"));
                Map<String,Object> showdown = castMap(other.get("showdown"));
                if (showdown != null) {
                    addIfUrl(out, "showdown_front", showdown.get("front_default"));
                    addIfUrl(out, "showdown_back", showdown.get("back_default"));
                    addIfUrl(out, "showdown_shiny", showdown.get("front_shiny"));
                }
            }
        } catch (ClassCastException ignored) {}

        // dedupe + prioridad + CAP 10
        List<SpriteDTO> dedup = out.stream()
                .filter(s -> s.url != null && !s.url.isBlank() && !"null".equalsIgnoreCase(s.url))
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(s -> s.url, Function.identity(), (a,b)->a, LinkedHashMap::new),
                        m -> new ArrayList<>(m.values())));

        Comparator<SpriteDTO> priority = Comparator
                .comparingInt((SpriteDTO s) -> labelRank(s.label))
                .thenComparing(s -> s.label, String.CASE_INSENSITIVE_ORDER);

        return dedup.stream().sorted(priority).limit(10).collect(Collectors.toList());
    }

    private int labelRank(String label) {
        if (label == null) return 999;
        return switch (label) {
            case "official", "official_shiny" -> 0;
            case "home", "home_shiny" -> 1;
            case "front_default" -> 2;
            case "front_shiny" -> 3;
            case "showdown_front", "showdown_shiny" -> 4;
            case "back_default" -> 5;
            case "back_shiny" -> 6;
            case "front_female", "front_shiny_female" -> 7;
            case "back_female", "back_shiny_female" -> 8;
            case "dream_world" -> 9;
            default -> 50;
        };
    }

    private void addIfUrl(List<SpriteDTO> acc, String label, Object maybeUrl) {
        if (maybeUrl == null) return;
        String u = String.valueOf(maybeUrl);
        if (u.isBlank() || "null".equalsIgnoreCase(u)) return;
        SpriteDTO s = new SpriteDTO(); s.label = label; s.url = u; acc.add(s);
    }

    @SuppressWarnings("unchecked")
    private String pickLocalized(Map<String,Object> species, String listKey, String valueKey) {
        List<Map<String,Object>> arr = (List<Map<String, Object>>) species.get(listKey);
        if (arr == null) return null;
        String es = null, en = null;
        for (Map<String,Object> it : arr) {
            Map<String,Object> lang = (Map<String, Object>) it.get("language");
            String ln = (lang != null) ? String.valueOf(lang.get("name")) : "";
            String val = String.valueOf(it.get(valueKey)).replaceAll("[\\n\\f]", " ");
            if (ln.equals("es") && (es == null || es.isBlank())) es = val;
            if (ln.equals("en") && (en == null || en.isBlank())) en = val;
            if (es != null && en != null) break;
        }
        return (es != null) ? es : en;
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> castMap(Object o) { return (o instanceof Map) ? (Map<String,Object>) o : null; }

    private Map<String,Object> jsonSafe(String line) {
        try { return json.readValue(line, new TypeReference<Map<String,Object>>(){}); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }

    /* ---------- Persistencia del snapshot ligero ---------- */

    private void persistToDisk(List<PokemonFullDTO> all) {
        try {
            Files.createDirectories(CACHE_FILE.getParent());
            Map<String,Object> wrapper = new LinkedHashMap<>();
            wrapper.put("lastUpdated", Instant.now().toString());
            wrapper.put("pokemons", all);
            Files.writeString(CACHE_FILE, json.writeValueAsString(wrapper));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void loadDiskCacheIfExists() throws IOException {
        if (!Files.exists(CACHE_FILE)) return;
        Map<String,Object> wrapper = json.readValue(Files.readAllBytes(CACHE_FILE), new TypeReference<>(){});
        Object lu = wrapper.get("lastUpdated");
        if (lu != null) lastUpdated = Instant.parse(lu.toString());
        List<PokemonFullDTO> all = json.convertValue(wrapper.get("pokemons"), new TypeReference<List<PokemonFullDTO>>(){});
        if (all == null) return;

        byId.clear(); cards.clear(); allTypes.clear();
        for (PokemonFullDTO pf : all) {
            byId.put(pf.id, pf);
            cards.add(toCard(pf));
            if (pf.types != null) allTypes.addAll(pf.types);
        }
    }

    /* ---------- Índice de conteos por archivo ---------- */

    private void writeCatalogIndex() {
        Map<String,Object> idx = new LinkedHashMap<>();
        idx.put("generatedAt", Instant.now().toString());
        Map<String,Integer> counts = new LinkedHashMap<>();
        for (String res : RESOURCE_ORDER) {
            String f = res.replace('-', '_') + ".ndjson";
            Path p = FULL_DIR.resolve(f);
            int c = 0;
            if (Files.exists(p)) {
                try (var lines = Files.lines(p)) { c = (int) lines.count(); } catch (IOException ignored) {}
            }
            counts.put(res, c);
        }
        idx.put("counts", counts);
        try { Files.writeString(CATALOG_IDX, json.writeValueAsString(idx)); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }

    /* ---------- util FS ---------- */
    private static class FilesX {
        static void ensureDir(Path p) {
            try { Files.createDirectories(p); } catch (IOException e) { throw new UncheckedIOException(e); }
        }
    }
}
