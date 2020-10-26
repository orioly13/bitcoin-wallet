package com.community.bitcoinwallet.controller;

import com.community.bitcoinwallet.model.WalletEntry;
import com.community.bitcoinwallet.model.requests.AddWalletEntryRequest;
import com.community.bitcoinwallet.model.requests.BalanceRequest;
import com.community.bitcoinwallet.model.response.GeneralResponseData;
import com.community.bitcoinwallet.model.response.Status;
import com.community.bitcoinwallet.model.response.WalletEntryResponse;
import com.community.bitcoinwallet.service.WalletService;
import com.community.bitcoinwallet.util.DateAndAmountUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
@Log4j2
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class WalletController {

    private static final GeneralResponseData ERROR_RESPONSE =
        new GeneralResponseData(Status.INTERNAL_ERROR, "Something went wrong");
    public static final GeneralResponseData OK_RESPONSE =
        new GeneralResponseData(Status.OK, "All good!");
    public static final GeneralResponseData WRONG_JSON_RESPONSE =
        new GeneralResponseData(Status.CLIENT_ERROR, "Wrong JSON!");

    WalletService walletService;

    @PostMapping(value = "/add-entry", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public GeneralResponseData addEntry(@RequestBody AddWalletEntryRequest entryRequest) {
        if (entryRequest == null || entryRequest.getDatetime() == null ||
            entryRequest.getAmount() == null) {
            throw new IllegalArgumentException("Request should not have null fields");
        }
        walletService.addEntry(
            new WalletEntry(entryRequest.getDatetime().toInstant(),
                DateAndAmountUtils.toBigDecimal(entryRequest.getAmount())));
        return OK_RESPONSE;
    }

    @PostMapping(value = "/balance", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public List<WalletEntryResponse> getBalance(@RequestBody BalanceRequest balanceRequest) {
        if (balanceRequest == null || balanceRequest.getFrom() == null ||
            balanceRequest.getTo() == null) {
            throw new IllegalArgumentException("Request should not have null fields");
        }
        List<WalletEntry> entries = walletService.getBalance(
            balanceRequest.getFrom().toInstant(),
            balanceRequest.getTo().toInstant());
        return entries.stream().map(walletEntry ->
            new WalletEntryResponse(
                DateAndAmountUtils.toUTCZonedDate(walletEntry.getDatetime()),
                walletEntry.getAmount().doubleValue()))
            .collect(Collectors.toList());
    }

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
        return ERROR_RESPONSE;
    }
}
