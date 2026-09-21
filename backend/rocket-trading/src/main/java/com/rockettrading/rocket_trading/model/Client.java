package com.rockettrading.rocket_trading.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Client {
    @Id
    private long clientId;
    private String name;
    private String email;


}
