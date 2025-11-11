package com.digis01.PokeAPICliente.ML;

public class Result<T> {
    public boolean correct;
    public String message;
    public T object;
    public boolean loading;   // para estados de precarga (UI)

    public static <T> Result<T> ok(T obj) {
        Result<T> r = new Result<>();
        r.correct = true;
        r.object = obj;
        r.loading = false;
        return r;
    }

    public static <T> Result<T> error(String msg) {
        Result<T> r = new Result<>();
        r.correct = false;
        r.message = msg;
        r.loading = false;
        return r;
    }

    public static <T> Result<T> loading(int progressPercent) {
        Result<T> r = new Result<>();
        r.correct = true;
        r.loading = true;
        r.message = "Cargando: " + progressPercent + "%";
        return r;
    }
}
