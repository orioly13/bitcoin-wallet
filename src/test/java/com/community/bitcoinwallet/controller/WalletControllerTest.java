package com.community.bitcoinwallet.controller;

import com.community.bitcoinwallet.BitcoinWalletApplication;
import com.community.bitcoinwallet.model.WalletEntry;
import com.community.bitcoinwallet.model.requests.AddWalletEntryRequest;
import com.community.bitcoinwallet.model.requests.BalanceRequest;
import com.community.bitcoinwallet.model.response.GeneralResponseData;
import com.community.bitcoinwallet.model.response.Status;
import com.community.bitcoinwallet.model.response.WalletEntryResponse;
import com.community.bitcoinwallet.repository.WalletRepository;
import com.community.bitcoinwallet.service.WalletService;
import com.community.bitcoinwallet.util.DateAndAmountUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.community.bitcoinwallet.controller.AbstractController.INTERNAL_ERROR_RESPONSE;
import static com.community.bitcoinwallet.controller.WalletController.OK_RESPONSE;
import static com.community.bitcoinwallet.controller.WalletController.WRONG_JSON_RESPONSE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@AutoConfigureMockMvc
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {BitcoinWalletApplication.class},
    properties = "spring.profiles.active=in-memory")
@Transactional
class WalletControllerTest {
    private final static String ENTRY = "/api/wallet/add-entry";
    private final static String BALANCE = "/api/wallet/balance";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private WalletController controller;
    @Autowired
    private WalletService service;
    @Autowired
    private WalletRepository repository;

    @BeforeEach
    public void setUp() {
        repository.clear();
        ReflectionTestUtils.setField(controller, "walletService", service);
    }


    @Test
    public void addEntryShouldSuccessfullyStoreData() throws Exception {
        MvcResult mvcResult = postJsonSuccess(ENTRY,
            new AddWalletEntryRequest(Instant.parse("2020-10-20T12:00:00Z")
                .atZone(ZoneOffset.UTC), 10.1));
        Assertions.assertThat(readJson(mvcResult, GeneralResponseData.class))
            .isEqualTo(OK_RESPONSE);
    }

    @Test
    public void addEntryShouldReturnErrorIfIncorrectDataPassed() throws Exception {
        MvcResult mvcResult = postJsonClientError(ENTRY, "this is not a json!");
        Assertions.assertThat(readJson(mvcResult, GeneralResponseData.class)).isEqualTo(WRONG_JSON_RESPONSE);

        mvcResult = postJsonClientError(ENTRY, null);
        Assertions.assertThat(readJson(mvcResult, GeneralResponseData.class))
            .isEqualTo(new GeneralResponseData(Status.CLIENT_ERROR, "Wrong JSON!"));

        mvcResult = postJsonClientError(ENTRY, new AddWalletEntryRequest(null, 1.1));
        Assertions.assertThat(readJson(mvcResult, GeneralResponseData.class))
            .isEqualTo(new GeneralResponseData(Status.CLIENT_ERROR, "Request should not have null fields"));

        mvcResult = postJsonClientError(ENTRY, new AddWalletEntryRequest(Instant.ofEpochMilli(1)
            .atZone(ZoneOffset.UTC), null));
        Assertions.assertThat(readJson(mvcResult, GeneralResponseData.class))
            .isEqualTo(new GeneralResponseData(Status.CLIENT_ERROR, "Request should not have null fields"));
    }

    @Test
    public void addEntryShouldReturnErrorIfNegativeAmountPassed() throws Exception {
        MvcResult mvcResult = postJsonClientError(ENTRY,
            new AddWalletEntryRequest(Instant.parse("2020-10-20T12:00:00Z")
                .atZone(ZoneOffset.UTC), -10.1));
        Assertions.assertThat(readJson(mvcResult, GeneralResponseData.class))
            .isEqualTo(new GeneralResponseData(Status.CLIENT_ERROR,
                "Amount is negative in entry:WalletEntry(datetime=2020-10-20T12:00:00Z, amount=-10.1)"));
    }

    @Test
    public void addEntryShouldReturnServerErrorIfServiceIsUnavailable() throws Exception {
        ReflectionTestUtils.setField(controller, "walletService", null);
        MvcResult mvcResult = postJsonServerError(ENTRY,
            new AddWalletEntryRequest(Instant.parse("2020-10-20T12:00:00Z")
                .atZone(ZoneOffset.UTC), 10.1));
        Assertions.assertThat(readJson(mvcResult, GeneralResponseData.class))
            .isEqualTo(INTERNAL_ERROR_RESPONSE);
    }

    @Test
    public void balanceShouldSuccessfullyRertrieveEntries() throws Exception {
        MvcResult mvcResult = postJsonSuccess(BALANCE,
            new BalanceRequest(Instant.parse("2020-10-11T10:30:00Z").atZone(ZoneOffset.UTC),
                Instant.parse("2020-10-11T11:45:00Z").atZone(ZoneOffset.UTC)));
        Assertions.assertThat(readJson(mvcResult, new TypeReference<List<WalletEntryResponse>>() {
        })).isEqualTo(Collections.singletonList(new WalletEntryResponse(Instant.parse("2020-10-11T11:00:00Z")
            .atZone(ZoneId.of("UTC")), 0.0)));

        repository.addEntry(new WalletEntry(Instant.parse("2020-10-20T12:10:00Z"),
            DateAndAmountUtils.toBigDecimal("10.1")));
        repository.addEntry(new WalletEntry(Instant.parse("2020-10-20T12:15:00Z"),
            DateAndAmountUtils.toBigDecimal("11.2")));

        mvcResult = postJsonSuccess(BALANCE,
            new BalanceRequest(Instant.parse("2020-10-11T11:30:00Z").atZone(ZoneOffset.UTC),
                Instant.parse("2020-10-11T12:45:00Z").atZone(ZoneOffset.UTC)));
        Assertions.assertThat(readJson(mvcResult, new TypeReference<List<WalletEntryResponse>>() {
        })).isEqualTo(Collections.singletonList(new WalletEntryResponse(Instant.parse("2020-10-11T12:00:00Z")
            .atZone(ZoneId.of("UTC")), 0.0)));

        mvcResult = postJsonSuccess(BALANCE,
            new BalanceRequest(Instant.parse("2020-10-20T12:30:00Z").atZone(ZoneOffset.UTC),
                Instant.parse("2020-10-20T13:30:00Z").atZone(ZoneOffset.UTC)));
        Assertions.assertThat(readJson(mvcResult, new TypeReference<List<WalletEntryResponse>>() {
        })).isEqualTo(Collections.singletonList(
            new WalletEntryResponse(Instant.parse("2020-10-20T13:00:00Z").atZone(ZoneId.of("UTC")), 21.3)));
    }

    @Test
    public void balanceShouldSuccessfullyFillEmptyHourss() throws Exception {
        repository.addEntry(new WalletEntry(Instant.parse("2020-10-20T12:10:00Z"),
            DateAndAmountUtils.toBigDecimal("10.1")));
        repository.addEntry(new WalletEntry(Instant.parse("2020-10-20T12:15:00Z"),
            DateAndAmountUtils.toBigDecimal("11.2")));
        repository.addEntry(new WalletEntry(Instant.parse("2020-10-20T14:15:00Z"),
            DateAndAmountUtils.toBigDecimal("11.2")));


        MvcResult mvcResult = postJsonSuccess(BALANCE,
            new BalanceRequest(Instant.parse("2020-10-20T12:30:00Z").atZone(ZoneOffset.UTC),
                Instant.parse("2020-10-20T15:30:00Z").atZone(ZoneOffset.UTC)));
        Assertions.assertThat(readJson(mvcResult, new TypeReference<List<WalletEntryResponse>>() {
        })).isEqualTo(Arrays.asList(
            new WalletEntryResponse(Instant.parse("2020-10-20T13:00:00Z").atZone(ZoneId.of("UTC")), 21.3),
            new WalletEntryResponse(Instant.parse("2020-10-20T14:00:00Z").atZone(ZoneId.of("UTC")), 21.3),
            new WalletEntryResponse(Instant.parse("2020-10-20T15:00:00Z").atZone(ZoneId.of("UTC")), 32.5)));
    }


    @Test
    public void balanceShouldProcessOtherTimeZones() throws Exception {
        repository.addEntry(new WalletEntry(Instant.parse("2020-10-20T12:10:00Z"),
            DateAndAmountUtils.toBigDecimal("10.1")));
        repository.addEntry(new WalletEntry(Instant.parse("2020-10-20T12:15:00Z"),
            DateAndAmountUtils.toBigDecimal("11.2")));
        repository.addEntry(new WalletEntry(Instant.parse("2020-10-20T14:15:00Z"),
            DateAndAmountUtils.toBigDecimal("11.2")));


        MvcResult mvcResult = postJsonSuccess(BALANCE,
            new BalanceRequest(Instant.parse("2020-10-20T12:30:00Z").atOffset(ZoneOffset.ofHours(-1)).toZonedDateTime(),
                Instant.parse("2020-10-20T15:30:00Z").atOffset(ZoneOffset.ofHours(-6)).toZonedDateTime()));
        Assertions.assertThat(readJson(mvcResult, new TypeReference<List<WalletEntryResponse>>() {
        })).isEqualTo(Arrays.asList(
            new WalletEntryResponse(Instant.parse("2020-10-20T13:00:00Z").atZone(ZoneId.of("UTC")), 21.3),
            new WalletEntryResponse(Instant.parse("2020-10-20T14:00:00Z").atZone(ZoneId.of("UTC")), 21.3),
            new WalletEntryResponse(Instant.parse("2020-10-20T15:00:00Z").atZone(ZoneId.of("UTC")), 32.5)));
    }


    @Test
    public void balanceShouldReturnErrorIfIncorrectDataPassed() throws Exception {
        MvcResult mvcResult = postJsonClientError(BALANCE, "this is not a json!");
        Assertions.assertThat(readJson(mvcResult, GeneralResponseData.class)).isEqualTo(WRONG_JSON_RESPONSE);

        mvcResult = postJsonClientError(BALANCE, null);
        Assertions.assertThat(readJson(mvcResult, GeneralResponseData.class))
            .isEqualTo(new GeneralResponseData(Status.CLIENT_ERROR, "Wrong JSON!"));

        mvcResult = postJsonClientError(BALANCE, new BalanceRequest(null, Instant.ofEpochMilli(1)
            .atZone(ZoneOffset.UTC)));
        Assertions.assertThat(readJson(mvcResult, GeneralResponseData.class))
            .isEqualTo(new GeneralResponseData(Status.CLIENT_ERROR, "Request should not have null fields"));

        mvcResult = postJsonClientError(BALANCE, new BalanceRequest(Instant.ofEpochMilli(1)
            .atZone(ZoneOffset.UTC), null));
        Assertions.assertThat(readJson(mvcResult, GeneralResponseData.class))
            .isEqualTo(new GeneralResponseData(Status.CLIENT_ERROR, "Request should not have null fields"));
    }

    @Test
    public void balanceShouldReturnErrorIfDatesCollide() throws Exception {
        MvcResult mvcResult = postJsonClientError(BALANCE,
            new BalanceRequest(Instant.parse("2020-10-11T10:30:00Z").atZone(ZoneOffset.UTC),
                Instant.parse("2020-10-11T10:00:00Z").atZone(ZoneOffset.UTC)));
        Assertions.assertThat(readJson(mvcResult, GeneralResponseData.class))
            .isEqualTo(new GeneralResponseData(Status.CLIENT_ERROR,
                "From and to should be in different hours: from=2020-10-11T10:30:00Z;to=2020-10-11T10:00:00Z"));
        mvcResult = postJsonClientError(BALANCE,
            new BalanceRequest(Instant.parse("2020-10-11T10:30:00Z").atZone(ZoneOffset.UTC),
                Instant.parse("2020-10-11T10:45:00Z").atZone(ZoneOffset.UTC)));
        Assertions.assertThat(readJson(mvcResult, GeneralResponseData.class))
            .isEqualTo(new GeneralResponseData(Status.CLIENT_ERROR,
                "From and to should be in different hours: from=2020-10-11T10:30:00Z;to=2020-10-11T10:45:00Z"));
    }

    @Test
    public void balanceShouldReturnServerErrorIfServiceIsUnavailable() throws Exception {
        ReflectionTestUtils.setField(controller, "walletService", null);
        MvcResult mvcResult = postJsonServerError(BALANCE,
            new BalanceRequest(Instant.parse("2020-10-11T10:30:00Z").atZone(ZoneOffset.UTC),
                Instant.parse("2020-10-11T11:45:00Z").atZone(ZoneOffset.UTC)));
        Assertions.assertThat(readJson(mvcResult, GeneralResponseData.class))
            .isEqualTo(INTERNAL_ERROR_RESPONSE);
    }

    private MvcResult postJsonSuccess(String url, Object body) throws Exception {
        return mvc.perform(
            post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(body)))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    }

    private MvcResult postJsonClientError(String url, Object body) throws Exception {
        return mvc.perform(
            post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(body)))
            .andExpect(status().is4xxClientError())
            .andReturn();
    }

    private MvcResult postJsonServerError(String url, Object body) throws Exception {
        return mvc.perform(
            post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(body)))
            .andExpect(status().is5xxServerError())
            .andReturn();
    }

    protected <T> T readJson(MvcResult content, Class<T> cls) throws IOException {
        return objectMapper.readValue(content.getResponse().getContentAsString(), cls);
    }

    protected <T> T readJson(MvcResult result, TypeReference<T> cls) throws IOException {
        return objectMapper.readValue(result.getResponse().getContentAsString(), cls);
    }
}