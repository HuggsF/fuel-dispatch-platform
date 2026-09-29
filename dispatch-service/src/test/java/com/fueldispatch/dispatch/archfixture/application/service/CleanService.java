package com.fueldispatch.dispatch.archfixture.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Allowed: an application service using {@code @Service} and {@code @Transactional}. */
@Service
public class CleanService {

    @Transactional(readOnly = true)
    public void query() {}
}
