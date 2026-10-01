package com.fueldispatch.tracking.archfixture.adapter.in.web;

import com.fueldispatch.tracking.archfixture.adapter.out.persistence.PersistenceAdapter;
import com.fueldispatch.tracking.archfixture.application.service.LeakyService;

/** Violations: an adapter depending on another adapter and on an application service. */
public class WebAdapter {

    private final PersistenceAdapter persistence = new PersistenceAdapter();
    private final LeakyService service = new LeakyService();

    public PersistenceAdapter persistence() {
        return persistence;
    }

    public LeakyService service() {
        return service;
    }
}
