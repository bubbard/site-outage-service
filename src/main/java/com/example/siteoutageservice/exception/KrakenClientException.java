package com.example.siteoutageservice.exception;

import lombok.Getter;

@Getter
public class KrakenClientException extends RuntimeException{

    private final int status;

    public KrakenClientException(String message, int status){
        super(message);
        this.status = status;
    }

}
