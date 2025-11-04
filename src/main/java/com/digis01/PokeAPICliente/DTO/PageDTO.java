package com.digis01.PokeAPICliente.DTO;

import java.util.List;

public class PageDTO<T> {
    public int page;
    public int size;
    public int total;
    public int totalPages;  // <-- usado por el service
    public List<T> items;
}
