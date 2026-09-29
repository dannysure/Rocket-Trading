package com.rockettrading.rocket_trading.model;

import java.util.ArrayList;
import java.util.List;

public class Watchlist {
    private String name;
    private List<Instrument> instruments;

    public Watchlist() {
        this.instruments = new ArrayList<>();
    }

    public Watchlist(String name) {
        setName(name);
        this.instruments = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.name = name.trim();
    }

    public List<Instrument> getInstruments() {
        return new ArrayList<>(instruments);
    }

    public void addInstrument(Instrument instrument) {
        if (instrument == null) {
            throw new IllegalArgumentException("instrument must not be null");
        }
        if (instruments.contains(instrument)) {
            throw new IllegalArgumentException("instrument " + instrument.getSymbol() + " is already in the watchlist");
        }
        instruments.add(instrument);
    }

    public void removeInstrument(Instrument instrument) {
        if (instrument == null) {
            throw new IllegalArgumentException("instrument must not be null");
        }
        if (!instruments.remove(instrument)) {
            throw new IllegalArgumentException("instrument " + instrument.getSymbol() + " is not in the watchlist");
        }
    }

    public int getInstrumentCount() {
        return instruments.size();
    }

    public boolean contains(Instrument instrument) {
        if (instrument == null) {
            return false;
        }
        return instruments.contains(instrument);
    }
}

