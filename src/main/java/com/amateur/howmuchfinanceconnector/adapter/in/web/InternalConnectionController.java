package com.amateur.howmuchfinanceconnector.adapter.in.web;

import com.amateur.howmuchfinanceconnector.application.ConnectionRegistrationRequest;
import com.amateur.howmuchfinanceconnector.application.ConnectionRegistrationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/connections")
public class InternalConnectionController {

    private final ConnectionRegistrationService service;

    public InternalConnectionController(ConnectionRegistrationService service) {
        this.service = service;
    }

    @PutMapping("/{connectionRef}")
    public ResponseEntity<Void> register(
            @PathVariable UUID connectionRef,
            @Valid @RequestBody ConnectionRegistrationRequest request
    ) {
        service.register(connectionRef, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{connectionRef}")
    public ResponseEntity<Void> revoke(@PathVariable UUID connectionRef) {
        service.revoke(connectionRef);
        return ResponseEntity.noContent().build();
    }
}
