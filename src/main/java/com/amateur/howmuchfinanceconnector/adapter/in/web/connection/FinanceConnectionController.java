package com.amateur.howmuchfinanceconnector.adapter.in.web.connection;

import com.amateur.howmuchfinanceconnector.application.port.in.connection.RegisterFinanceConnectionUseCase;
import com.amateur.howmuchfinanceconnector.application.port.in.connection.RevokeFinanceConnectionUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/connections")
public class FinanceConnectionController {

    private final RegisterFinanceConnectionUseCase registerUseCase;
    private final RevokeFinanceConnectionUseCase revokeUseCase;

    public FinanceConnectionController(
            RegisterFinanceConnectionUseCase registerUseCase,
            RevokeFinanceConnectionUseCase revokeUseCase
    ) {
        this.registerUseCase = registerUseCase;
        this.revokeUseCase = revokeUseCase;
    }

    @PutMapping("/{connectionRef}")
    public FinanceConnectionRegistrationResponse register(
            @PathVariable UUID connectionRef,
            @Valid @RequestBody RegisterFinanceConnectionRequest request
    ) {
        return FinanceConnectionRegistrationResponse.from(registerUseCase.register(request.toCommand(connectionRef)));
    }

    @DeleteMapping("/{connectionRef}")
    public ResponseEntity<Void> revoke(@PathVariable UUID connectionRef) {
        revokeUseCase.revoke(connectionRef);
        return ResponseEntity.noContent().build();
    }
}
