package com.tachibana.downloader.core.exception;

public class HttpException extends Exception
{
    private final int responseCode;

    public HttpException(String message)
    {
        this(message, 0);
    }

    public HttpException(String message, int responseCode)
    {
        super(message);
        this.responseCode = responseCode;
    }

    public HttpException(String message, int responseCode, Exception e)
    {
        this(message, responseCode);
        initCause(e);
    }

    public int getResponseCode()
    {
        return responseCode;
    }
}