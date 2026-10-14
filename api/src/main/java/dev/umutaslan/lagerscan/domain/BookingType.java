package dev.umutaslan.lagerscan.domain;

public enum BookingType {
    /** Goods receipt into a bin. */ RECEIPT,
    /** Withdrawal from a bin (production, service). */ ISSUE,
    /** Move between two bins. */ TRANSFER,
    /** Stocktaking count of one bin. */ COUNT
}
