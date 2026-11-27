package com.jftse.server.core.service.impl;

import com.jftse.entities.database.model.auth.AuthToken;
import com.jftse.entities.database.repository.auth.AuthTokenRepository;
import com.jftse.server.core.service.AuthTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Login tokens are stored only as their SHA-256 hex digest: a copy of the authtoken table
 * cannot be replayed as a login. Callers keep handing the raw token in and out; the
 * hashing happens here on save and on every lookup.
 */
@Service
@RequiredArgsConstructor
public class AuthTokenServiceImpl implements AuthTokenService {
    private final AuthTokenRepository authTokenRepository;

    static String hashToken(String token) {
        if (token == null)
            return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AuthToken save(AuthToken authToken) {
        authToken.setToken(hashToken(authToken.getToken()));
        return authTokenRepository.save(authToken);
    }

    @Override
    @Transactional
    public void remove(AuthToken authToken) {
        authTokenRepository.findById(authToken.getId()).ifPresent(at -> authTokenRepository.deleteById(at.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthToken findAuthToken(String token, Long timestamp, String accountName) {
        Optional<AuthToken> optional = authTokenRepository.findAuthTokenByTokenAndLoginTimestampAndAccountName(hashToken(token), timestamp, accountName);
        return optional.orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthToken findAuthToken(String token) {
        Optional<AuthToken> optional = authTokenRepository.findAuthTokenByToken(hashToken(token));
        return optional.orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuthToken> findAuthTokensByAccountName(String accountName) {
        return authTokenRepository.findAuthTokensByAccountName(accountName);
    }
}
