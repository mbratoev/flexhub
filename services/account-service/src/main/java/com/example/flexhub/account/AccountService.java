package com.example.flexhub.account;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// All balance mutations go through this service so they're (a) wrapped in a DB transaction
// and (b) protected by a row-level lock taken via repository.findByIdForUpdate(...).
// Two concurrent transfers on the same source account will serialize at the row.
@Service
public class AccountService {

    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public DebitResult debit(UUID accountId, BigDecimal amount) {
        Optional<Account> opt = repository.findByIdForUpdate(accountId);
        if (opt.isEmpty()) {
            return DebitResult.accountNotFound();
        }
        Account account = opt.get();
        if (account.getBalance().compareTo(amount) < 0) {
            return DebitResult.insufficientFunds(account.getBalance());
        }
        account.debit(amount);
        // JPA dirty-checking will flush the balance update at transaction commit.
        return DebitResult.success(account);
    }

    @Transactional
    public CreditResult credit(UUID accountId, BigDecimal amount) {
        Optional<Account> opt = repository.findByIdForUpdate(accountId);
        if (opt.isEmpty()) {
            return CreditResult.accountNotFound();
        }
        Account account = opt.get();
        if (account.isRejectsCredits()) {
            return CreditResult.rejectsCredits();
        }
        account.credit(amount);
        return CreditResult.success(account);
    }

    // Convenience: unconditional credit for the compensation step. Used to refund the source
    // after a CreditFailed event. The source account is by definition active (we already
    // debited from it successfully), so no rejection check.
    @Transactional
    public Optional<Account> creditUnconditional(UUID accountId, BigDecimal amount) {
        return repository.findByIdForUpdate(accountId)
                .map(account -> {
                    account.credit(amount);
                    return account;
                });
    }

    @Transactional(readOnly = true)
    public Optional<Account> findById(UUID accountId) {
        return repository.findById(accountId);
    }

    public Account save(Account account) {
        return repository.save(account);
    }

    public sealed interface DebitResult {
        record Success(Account account) implements DebitResult {}
        record InsufficientFunds(BigDecimal currentBalance) implements DebitResult {}
        record AccountNotFound() implements DebitResult {}

        static DebitResult success(Account account) { return new Success(account); }
        static DebitResult insufficientFunds(BigDecimal currentBalance) { return new InsufficientFunds(currentBalance); }
        static DebitResult accountNotFound() { return new AccountNotFound(); }
    }

    public sealed interface CreditResult {
        record Success(Account account) implements CreditResult {}
        record AccountNotFound() implements CreditResult {}
        record RejectsCredits() implements CreditResult {}

        static CreditResult success(Account account) { return new Success(account); }
        static CreditResult accountNotFound() { return new AccountNotFound(); }
        static CreditResult rejectsCredits() { return new RejectsCredits(); }
    }
}
