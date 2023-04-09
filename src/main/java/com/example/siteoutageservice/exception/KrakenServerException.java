package com.example.siteoutageservice.exception;

import lombok.Getter;

@Getter
public class KrakenServerException extends RuntimeException{

    private final int status;

    public KrakenServerException(String message, int status){
        super(message);
        this.status = status;
    }

}
