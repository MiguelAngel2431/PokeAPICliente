package com.digis01.PokeAPICliente.DTO;

import java.util.List;
import java.util.Map;

public class PokemonCardDTO {
    public int id;
    public String name;
    public String image;
    public List<String> types;
    public Map<String,Integer> stats;
    public double heightM;
    public double weightKg;
    public int baseExp;
}
