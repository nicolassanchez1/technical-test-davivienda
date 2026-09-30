package io.github.nicolassanchez1.technicaltestdavivienda.support;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A transaction manager that commits nothing and remembers everything, so a use case can be asked
 * whether it committed its unit of work or rolled it back without a database being involved. A real
 * manager reacts to the rollback-only flag at commit time, and so does this one.
 */
public final class RecordingTransactionManager implements PlatformTransactionManager {

    private int commits;
    private int rollbacks;

    public TransactionTemplate template() {
        return new TransactionTemplate(this);
    }

    public int commits() {
        return commits;
    }

    public int rollbacks() {
        return rollbacks;
    }

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
        return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
        if (status.isRollbackOnly()) {
            rollbacks++;
            return;
        }
        commits++;
    }

    @Override
    public void rollback(TransactionStatus status) {
        rollbacks++;
    }
}
