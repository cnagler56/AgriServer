package com.home.Domain;


public enum Role {
    USER,
    ADMIN,
    /** Third-party market-analysis provider: can publish analysis and manage
        their own subscriber list, but has no site-admin powers. */
    ANALYST

}
