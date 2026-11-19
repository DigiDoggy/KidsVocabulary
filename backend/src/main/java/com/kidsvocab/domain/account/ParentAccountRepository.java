package com.kidsvocab.domain.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ParentAccountRepository extends JpaRepository<ParentAccount, Long> {

    Optional<ParentAccount> findByEmail(String email);

    boolean existsByEmail(String email);
}
