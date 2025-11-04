package com.digis01.PokeAPICliente.Service;

import com.digis01.PokeAPICliente.DTO.*;
import com.digis01.PokeAPICliente.ML.Result;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PokedexCacheService {

    // ===== Config (igual que ya tienes) =====
    private static final int THREADS = Math.max(8, Runtime.getRuntime().availableProcessors() * 2);
    private static final int PAGE_FETCH = 200;
    private static final java.nio.file.Path CACHE_FILE = java.nio.file.Paths.get("cache", "pokedex.json");
    private static final long MAX_AGE_SECONDS = 60L * 60L * 3L;

    // ===== Infra =====
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper json = new ObjectMapper();
    private final ExecutorService pool = new ThreadPoolExecutor(
            THREADS, THREADS, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
            r -> { Thread t = new Thread(r, "poke-loader"); t.setDaemon(true); return t; });

    // ===== Estado =====
    private final AtomicBoolean warming = new AtomicBoolean(); // <-- corrige nombre
    private final AtomicInteger progress = new AtomicInteger(0);
    private final AtomicInteger goal = new AtomicInteger();
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private volatile Instant lastUpdated = null;

    // Caché
    private final Map<Integer, PokemonFullDTO> byId = new ConcurrentHashMap<>();
    private final List<PokemonCardDTO> cards = new CopyOnWriteArrayList<>();

    // NUEVO: todos los tipos
    private final Set<String> allTypes = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void tryLoadCacheFromDiskOnBoot() {
        try {
            loadDiskCacheIfExists();
            if (cards.isEmpty()) warmupAsync(false);
        } catch (Exception e) {
            lastError.set("Cache boot error: " + e.getMessage());
        }
    }

    // ===== API pública =====

    public Result<Void> warmupAsync(boolean force) {
        if (warming.get() && !force) return Result.loading(currentProgress());
        if (!force && isCacheFresh()) return Result.ok(null);
        if (warming.compareAndSet(false, true)) {
            lastError.set(null); progress.set(0); goal.set(0);
            pool.submit(this::loadAllFromPokeApiAndPersist);
            return Result.loading(0);
        }
        return Result.loading(currentProgress());
    }

    public Result<PageDTO<PokemonCardDTO>> getIndexPage(int page, int size) {
        if (warming.get() && cards.isEmpty()) return Result.loading(currentProgress());
        if (cards.isEmpty()) { warmupAsync(false); return Result.loading(currentProgress()); }

        return paginate(cards, page, size);
    }

    // NUEVO: búsqueda global por nombre (contains, case-insensitive)
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

    // NUEVO: todos los tipos ordenados
    public List<String> getAllTypes() {
        return allTypes.stream()
                .filter(t -> !t.equals("unknown") && !t.equals("shadow"))
                .sorted()
                .collect(Collectors.toList());
    }
    
    
    // Devuelve TODAS las cards en memoria (para búsqueda/filtrado global)
public List<PokemonCardDTO> allCards() {
    return new ArrayList<>(cards); // cards es tu CopyOnWriteArrayList<>
}

// Devuelve TODOS los tipos presentes en cache (para el dropdown)
public Set<String> allTypes() {
    Set<String> out = new TreeSet<>();
    for (PokemonCardDTO c : cards) {
        if (c.types != null) out.addAll(c.types);
    }
    return out;
}

    public Result<PokemonFullDTO> getById(int id) {
        PokemonFullDTO p = byId.get(id);
        if (p != null) return Result.ok(p);
        warmupAsync(false);
        return Result.loading(currentProgress());
    }

    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("warming", warming.get());
        m.put("progress", currentProgress());
        m.put("count", cards.size());
        m.put("lastUpdated", lastUpdated);
        m.put("error", lastError.get());
        return m;
    }

    // ===== Internals =====

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

    private void loadAllFromPokeApiAndPersist() {
        try {
            Map<String,Object> root = http.getForObject("https://pokeapi.co/api/v2/pokemon", Map.class);
            int total = ((Number) root.getOrDefault("count", 0)).intValue();
            goal.set(Math.max(total, 1));

            List<String> detailUrls = new ArrayList<>(total);
            for (int offset = 0; offset < total; offset += PAGE_FETCH) {
                String url = "https://pokeapi.co/api/v2/pokemon?offset=" + offset + "&limit=" + Math.min(PAGE_FETCH, total - offset);
                Map<String,Object> page = http.getForObject(url, Map.class);
                List<Map<String,Object>> results = (List<Map<String,Object>>) page.getOrDefault("results", Collections.emptyList());
                for (Map<String,Object> r : results) {
                    Object u = r.get("url");
                    if (u != null) detailUrls.add(u.toString());
                }
            }

            progress.set(0);
            List<CompletableFuture<PokemonFullDTO>> futures = detailUrls.stream()
                    .map(u -> CompletableFuture.supplyAsync(() -> loadOne(u), pool)
                            .whenComplete((x, e) -> progress.incrementAndGet()))
                    .collect(Collectors.toList());

            List<PokemonFullDTO> all = futures.stream().map(f -> {
                try { return f.get(); } catch (Exception ignored) { return null; }
            }).filter(Objects::nonNull)
              .sorted(Comparator.comparingInt(p -> p.id))
              .collect(Collectors.toList());

            Map<Integer, PokemonFullDTO> idMap = new HashMap<>(all.size());
            List<PokemonCardDTO> cardList = new ArrayList<>(all.size());
            Set<String> typeSet = new HashSet<>();

            for (PokemonFullDTO pf : all) {
                idMap.put(pf.id, pf);
                cardList.add(toCard(pf));
                if (pf.types != null) typeSet.addAll(pf.types);
            }

            byId.clear(); byId.putAll(idMap);
            cards.clear(); cards.addAll(cardList);
            allTypes.clear(); allTypes.addAll(typeSet);

            persistToDisk(all);
            lastUpdated = Instant.now();
            lastError.set(null);
        } catch (Exception e) {
            lastError.set("Warmup failed: " + e.getMessage());
        } finally {
            warming.set(false);
        }
    }

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

    private PokemonFullDTO loadOne(String detailUrl) {
        Map<String,Object> d = http.getForObject(detailUrl, Map.class);
        if (d == null) return null;

        PokemonFullDTO p = new PokemonFullDTO();
        p.id = ((Number) d.getOrDefault("id", 0)).intValue();
        p.name = (String) d.getOrDefault("name", "pokemon");
        p.image = extractPrimaryImage(d);

        // tipos
        p.types = new ArrayList<>();
        Object typesObj = d.get("types");
        if (typesObj instanceof List) {
            for (Object t : (List<?>) typesObj) {
                Map<String,Object> tm = (Map<String, Object>) t;
                Map<String,Object> type = (Map<String, Object>) tm.get("type");
                if (type != null && type.get("name") != null) p.types.add(type.get("name").toString());
            }
        }

        // stats
        Map<String,Integer> stats = new LinkedHashMap<>();
        stats.put("hp", 0); stats.put("attack", 0); stats.put("defense", 0); stats.put("speed", 0);
        Object statsObj = d.get("stats");
        if (statsObj instanceof List) {
            for (Object s : (List<?>) statsObj) {
                Map<String,Object> sm = (Map<String, Object>) s;
                int base = ((Number) sm.getOrDefault("base_stat", 0)).intValue();
                Map<String,Object> stat = (Map<String, Object>) sm.get("stat");
                if (stat == null) continue;
                String n = (String) stat.get("name");
                switch (n) {
                    case "hp" -> stats.put("hp", base);
                    case "attack" -> stats.put("attack", base);
                    case "defense" -> stats.put("defense", base);
                    case "special-attack" -> p.specialAttack = base;
                    case "special-defense" -> p.specialDefense = base;
                    case "speed" -> stats.put("speed", base);
                }
            }
        }
        p.stats = stats;

        p.heightM = ((Number) d.getOrDefault("height", 0)).doubleValue() / 10.0;
        p.weightKg = ((Number) d.getOrDefault("weight", 0)).doubleValue() / 10.0;
        p.baseExp = ((Number) d.getOrDefault("base_experience", 0)).intValue();

        // sprites (CAP a 10)
        p.sprites = extractAllSprites(d);

        // abilities
        p.abilities = new ArrayList<>();
        Object abObj = d.get("abilities");
        if (abObj instanceof List) {
            for (Object a : (List<?>) abObj) {
                Map<String,Object> am = (Map<String, Object>) a;
                Map<String,Object> ability = (Map<String, Object>) am.get("ability");
                if (ability != null && ability.get("name") != null) {
                    AbilityDTO ab = new AbilityDTO();
                    ab.name = ability.get("name").toString();
                    ab.hidden = Boolean.TRUE.equals(am.get("is_hidden"));
                    p.abilities.add(ab);
                }
            }
        }

        // species + cry
        try {
            Map<String,Object> species = (Map<String, Object>) d.get("species");
            if (species != null && species.get("url") != null) {
                String sUrl = species.get("url").toString();
                Map<String,Object> sp = http.getForObject(sUrl, Map.class);
                if (sp != null) {
                    p.genus = pickLocalized(sp, "genera", "genus");
                    p.flavor = pickLocalized(sp, "flavor_text_entries", "flavor_text");
                    try {
                        Map<String,Object> cries = (Map<String, Object>) d.get("cries");
                        if (cries != null) {
                            Object latest = cries.get("latest");
                            Object legacy = cries.get("legacy");
                            p.cry = latest != null ? latest.toString() : (legacy != null ? legacy.toString() : null);
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}

        return p;
    }

    private String extractPrimaryImage(Map<String,Object> d) {
        String image = null;
        try {
            Map<String,Object> sprites = (Map<String, Object>) d.get("sprites");
            if (sprites != null) {
                Map<String,Object> other = (Map<String, Object>) sprites.get("other");
                if (other != null) {
                    Map<String,Object> official = (Map<String, Object>) other.get("official-artwork");
                    if (official != null) image = (String) official.get("front_default");
                    if (image == null) {
                        Map<String,Object> dream = (Map<String, Object>) other.get("dream_world");
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
            Map<String,Object> sprites = (Map<String, Object>) d.get("sprites");
            if (sprites == null) return out;

            // directos
            addIfUrl(out, "front_default", sprites.get("front_default"));
            addIfUrl(out, "back_default", sprites.get("back_default"));
            addIfUrl(out, "front_shiny", sprites.get("front_shiny"));
            addIfUrl(out, "back_shiny", sprites.get("back_shiny"));
            addIfUrl(out, "front_female", sprites.get("front_female"));
            addIfUrl(out, "back_female", sprites.get("back_female"));
            addIfUrl(out, "front_shiny_female", sprites.get("front_shiny_female"));
            addIfUrl(out, "back_shiny_female", sprites.get("back_shiny_female"));

            // other/home/official/dream_world/showdown
            Map<String,Object> other = (Map<String, Object>) sprites.get("other");
            if (other != null) {
                Map<String,Object> home = (Map<String, Object>) other.get("home");
                if (home != null) {
                    addIfUrl(out, "home", home.get("front_default"));
                    addIfUrl(out, "home_shiny", home.get("front_shiny"));
                }
                Map<String,Object> official = (Map<String, Object>) other.get("official-artwork");
                if (official != null) {
                    addIfUrl(out, "official", official.get("front_default"));
                    addIfUrl(out, "official_shiny", official.get("front_shiny"));
                }
                Map<String,Object> dream = (Map<String, Object>) other.get("dream_world");
                if (dream != null) {
                    addIfUrl(out, "dream_world", dream.get("front_default"));
                }
                Map<String,Object> showdown = (Map<String, Object>) other.get("showdown");
                if (showdown != null) {
                    addIfUrl(out, "showdown_front", showdown.get("front_default"));
                    addIfUrl(out, "showdown_back", showdown.get("back_default"));
                    addIfUrl(out, "showdown_shiny", showdown.get("front_shiny"));
                }
            }
        } catch (ClassCastException ignored) {}

        // dedupe por URL manteniendo orden
        List<SpriteDTO> dedup = out.stream()
                .filter(s -> s.url != null && !s.url.isBlank() && !"null".equalsIgnoreCase(s.url))
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(s -> s.url, Function.identity(), (a,b)->a, LinkedHashMap::new),
                        m -> new ArrayList<>(m.values())));

        // PRIORIDAD y CAP a 10 (reordena según “mejores primero”)
        Comparator<SpriteDTO> priority = Comparator
                .comparingInt((SpriteDTO s) -> labelRank(s.label))
                .thenComparing(s -> s.label, String.CASE_INSENSITIVE_ORDER);

        return dedup.stream()
                .sorted(priority)
                .limit(10)
                .collect(Collectors.toList());
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

    private String pickLocalized(Map<String,Object> species, String listKey, String valueKey) {
        List<Map<String,Object>> arr = (List<Map<String, Object>>) species.get(listKey);
        if (arr == null) return null;
        String es = null, en = null;
        for (Map<String,Object> it : arr) {
            Map<String,Object> lang = (Map<String, Object>) it.get("language");
            String ln = lang != null ? String.valueOf(lang.get("name")) : "";
            String val = String.valueOf(it.get(valueKey)).replaceAll("[\\n\\f]", " ");
            if (ln.equals("es") && (es == null || es.isBlank())) es = val;
            if (ln.equals("en") && (en == null || en.isBlank())) en = val;
            if (es != null && en != null) break;
        }
        return es != null ? es : en;
    }

    // ===== Persistencia en disco (igual que ya tienes) =====
    private void persistToDisk(List<PokemonFullDTO> all) { /* igual */ }
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
}
