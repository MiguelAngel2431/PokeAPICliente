package com.digis01.PokeAPICliente.DTO;

import java.util.List;
import java.util.Map;

public class PokemonFullDTO {
    public int id;
    public String name;
    public String image;

    public List<String> types;
    public Map<String,Integer> stats;
    public int specialAttack;
    public int specialDefense;

    public double heightM;
    public double weightKg;
    public int baseExp;

    public List<SpriteDTO> sprites;
    public List<AbilityDTO> abilities;

    public String genus;
    public String flavor;
    public String cry;
    
    public boolean isDefault;
    public Integer order;
    public String locationAreaEncounters;
    public NamedAPIResource species;           
    public List<NamedAPIResource> forms;       
    public List<GameIndexDTO> gameIndices;     
    public List<HeldItemDTO> heldItems;        
    public List<MoveDTO> moves;                
    public List<StatEntryDTO> rawStats;       
    public List<TypeSlotDTO> rawTypes;         
    public String cryLatest;                   
    public String cryLegacy; 
    public String habitat;    
    public String generation;
    
    
    
  
}
