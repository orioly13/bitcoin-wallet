package com.community.bitcoinwallet.controller;

import com.community.bitcoinwallet.model.response.GeneralResponseData;
import com.community.bitcoinwallet.model.response.Status;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@Log4j2
public abstract class AbstractController {

    public static final GeneralResponseData INTERNAL_ERROR_RESPONSE =
        new GeneralResponseData(Status.INTERNAL_ERROR, "Something went wrong");
    public static final GeneralResponseData OK_RESPONSE =
        new GeneralResponseData(Status.OK, "All good!");
    public static final GeneralResponseData WRONG_JSON_RESPONSE =
        new GeneralResponseData(Status.CLIENT_ERROR, "Wrong JSON!");

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    @ResponseBody
    public GeneralResponseData handleException(IllegalArgumentException e) {
        return new GeneralResponseData(Status.CLIENT_ERROR, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    @ResponseBody
    public GeneralResponseData handleException(HttpMessageNotReadableException e) {
        return WRONG_JSON_RESPONSE;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public GeneralResponseData handleException(Exception e) {
        log.error("Something went wrong:", e);
        return INTERNAL_ERROR_RESPONSE;
    }
}
