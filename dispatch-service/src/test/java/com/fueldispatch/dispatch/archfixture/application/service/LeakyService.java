package com.fueldispatch.dispatch.archfixture.application.service;

import com.fueldispatch.dispatch.archfixture.adapter.out.persistence.PersistenceAdapter;

/** Violation: an application service depending on an adapter. */
public class LeakyService {

    private final PersistenceAdapter adapter = new PersistenceAdapter();

    public PersistenceAdapter adapter() {
        return adapter;
    }
}
