package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.dto.instrument.SupportedInstrumentResponse;
import com.rockettrading.rocket_trading.dto.user.ClientProfileResponse;
import com.rockettrading.rocket_trading.exception.NotFoundException;
import com.rockettrading.rocket_trading.model.Client;
import com.rockettrading.rocket_trading.repository.ClientRepository;
import com.rockettrading.rocket_trading.repository.InstrumentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    private final ClientRepository clientRepository;
    private final InstrumentRepository instrumentRepository;

    public UserService(ClientRepository clientRepository, InstrumentRepository instrumentRepository) {
        this.clientRepository = clientRepository;
        this.instrumentRepository = instrumentRepository;
    }

    public ClientProfileResponse getProfile(long clientId) {
        Client client = clientRepository.findById(clientId);
        if (client == null) {
            throw new NotFoundException("CLIENT_NOT_FOUND", "Client profile was not found for the signed-in session");
        }
        return ClientProfileResponse.from(client);
    }

    public List<SupportedInstrumentResponse> listSupportedInstruments() {
        return instrumentRepository.findSupportedInstruments().stream()
                .map(SupportedInstrumentResponse::from)
                .toList();
    }
}
