package io.github.yyxff.stockarena.service;

import io.github.yyxff.stockarena.model.Account;
import io.github.yyxff.stockarena.model.User;
import io.github.yyxff.stockarena.repository.AccountRepository;
import io.github.yyxff.stockarena.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final BigDecimal INIT_BALANCE = new BigDecimal(10000);
    @Autowired
    private AccountRepository accountRepository;


    @Transactional
    public User register(String username, String password) {

        // 1. Create user
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);

        // 2. Create account
        Account account = new Account();
        account.setUserId(user.getId());
        account.setAvailableBalance(INIT_BALANCE);
        account.setFrozenBalance(BigDecimal.ZERO);
        accountRepository.save(account);
        return user;
    }

    public Optional<User> login(String username, String password) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent() && passwordEncoder.matches(password, userOpt.get().getPassword())) {
            return userOpt;
        }
        return Optional.empty();
    }

}
