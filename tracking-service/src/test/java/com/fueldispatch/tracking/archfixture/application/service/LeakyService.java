package com.fueldispatch.tracking.archfixture.application.service;

import com.fueldispatch.tracking.archfixture.adapter.out.persistence.PersistenceAdapter;

/** Violation: an application service depending on an adapter. */
public class LeakyService {

    private final PersistenceAdapter adapter = new PersistenceAdapter();

    public PersistenceAdapter adapter() {
        return adapter;
    }
}
