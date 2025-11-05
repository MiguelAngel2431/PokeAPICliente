package com.digis01.PokeAPICliente.DTO;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class CatalogStatusDTO {
    public boolean warming;
    public int progress;        // % global
    public Integer goal;        // objetivo global de detalles a descargar (si aplica)
    public int count;           // pokémon listos (cards)
    public Instant lastUpdated;
    public String error;

    // breakdown por recurso: { "pokemon": {"goal":N, "done":M}, ... }
    public Map<String, Map<String, Integer>> resources = new LinkedHashMap<>();
}
