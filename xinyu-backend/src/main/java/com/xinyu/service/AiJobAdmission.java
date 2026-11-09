package com.xinyu.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** 单实例准入：容量查询与保存事务提交受同一把锁保护，绝不包住网络调用。 */
@Component
public class AiJobAdmission {
    private final Object admissionLock = new Object();
    private final TransactionTemplate transactions;

    public AiJobAdmission(PlatformTransactionManager manager) {
        this.transactions = new TransactionTemplate(manager);
    }

    /** 不允许加入调用方事务，否则锁释放时数据库可能尚未提交，容量保护会失效。 */
    public <T> T save(Supplier<T> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("任务准入必须作为最外层事务入口");
        }
        synchronized (admissionLock) {
            return transaction(work);
        }
    }

    /** 删除不增加容量，无需占用准入锁；来源行锁仍由这个真实数据库事务保持。 */
    public <T> T transaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }
}
