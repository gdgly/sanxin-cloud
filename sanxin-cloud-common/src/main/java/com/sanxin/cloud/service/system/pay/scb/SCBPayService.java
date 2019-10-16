package com.sanxin.cloud.service.system.pay.scb;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sanxin.cloud.common.FunctionUtils;
import com.sanxin.cloud.common.scbpay.SCBConfig;
import com.sanxin.cloud.common.scbpay.SCBHttpsUtils;
import com.sanxin.cloud.config.redis.RedisUtilsService;
import com.sanxin.cloud.enums.LoginChannelEnums;
import com.sanxin.cloud.exception.ThrowJsonException;
import com.sanxin.cloud.service.system.login.LoginDto;
import com.sanxin.cloud.service.system.login.LoginTokenService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * @author arno
 * @version 1.0
 * @date 2019-10-14
 */
@Service
public class SCBPayService {

    @Autowired
    private LoginTokenService loginTokenService;
    @Autowired
    private RedisUtilsService redisUtilsService;
    /**
     * SCB 用户授权
     * @param token
     */
    public String authorize(String token, LoginChannelEnums channelEnums){

        //获取用户信息
        LoginDto loginDto=loginTokenService.validLoginLoginDto(token);
        Map<String, String> params =new HashMap<>();

        HttpHeaders headers = new HttpHeaders();
        headers.add("apikey", SCBConfig.apikey);
        headers.add("apisecret", SCBConfig.apisecret);
        headers.add("resourceOwnerId",token);
        headers.add("requestUId",String.valueOf(loginDto.getTid()));
        headers.add("response-channel","mobile");
        if(FunctionUtils.isEquals(LoginChannelEnums.APP.getChannel(),channelEnums.getChannel())){
            headers.add("endState","mobile_app");
        }else{
            headers.add("endState","mobile_web");
        }
        RestTemplate restTemplate=new RestTemplate();
        HttpEntity<String> requestEntity = new HttpEntity<String>(null, headers);
        ResponseEntity<String> response = restTemplate.exchange(SCBConfig.url+SCBConfig.authorize, HttpMethod.GET, requestEntity, String.class);
        String result = response.getBody();
        if(StringUtils.isEmpty(result)){
            throw new ThrowJsonException("SCB AUTHORIZE REQUEST ERROR");
        }
        JSONObject jsonObject=JSONObject.parseObject(result);
        JSONObject statusObj=jsonObject.getJSONObject("status");
        if(!"1000".equals(statusObj.getString("code"))){
            throw new ThrowJsonException(statusObj.getString("description"));
        }
        JSONObject urlObj=jsonObject.getJSONObject("data");
        String callbackUrl=urlObj.getString("callbackUrl");
        System.out.println(callbackUrl);
        return callbackUrl;
    }

    /**
     * 根据授权码获取token
     * @param token
     * @param authCode
     */
    public String getToken(String token,String authCode){
        //获取用户信息
        LoginDto loginDto=loginTokenService.validLoginLoginDto(token);
        //获取token如果不存在的情况下就重新获取
        String key=loginDto.getTid()+"_scbtoken";
        String accessToken=redisUtilsService.getKey(key);
        if(!StringUtils.isEmpty(accessToken) && !"null".equals(accessToken)){
            String isTimeout=redisUtilsService.getKey(accessToken);
            if(StringUtils.isEmpty(isTimeout) || "null".equals(isTimeout)){
                return refresh(token);
            }
            return accessToken;

        }

        HttpHeaders params =new HttpHeaders();
        params.add("applicationKey",SCBConfig.apikey);
        params.add("applicationSecret",SCBConfig.apisecret);
//        params.add("authCode",authCode);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("accept-language", "EN");
        headers.add("requestUId",String.valueOf(loginDto.getTid()));
        headers.add("resourceOwnerId",token);

        HttpEntity<MultiValueMap> requestEntity = new HttpEntity<MultiValueMap>(params, headers);
        RestTemplate restTemplate=new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(SCBConfig.url+SCBConfig.getToken, HttpMethod.POST, requestEntity, String.class);
        String result = response.getBody();
        if(StringUtils.isEmpty(result)){
            throw new ThrowJsonException("SCB AUTHORIZE REQUEST ERROR");
        }

        JSONObject jsonObject=JSONObject.parseObject(result);
        JSONObject statusObj=jsonObject.getJSONObject("status");
        if(!"1000".equals(statusObj.getString("code"))){
            throw new ThrowJsonException(statusObj.getString("description"));
        }
        JSONObject dataObj=jsonObject.getJSONObject("data");
        accessToken =dataObj.getString("accessToken");
        redisUtilsService.setKey(key,accessToken);
        redisUtilsService.setKey(accessToken,dataObj.getString("expiresAt"),dataObj.getInteger("expiresIn"));
        return accessToken;
    }

    /**
     * 刷新token
     * @param token
     */
    public String refresh(String token){
        //获取用户信息
        LoginDto loginDto=loginTokenService.validLoginLoginDto(token);
        //获取token如果不存在的情况下就重新获取
        String key=loginDto.getTid()+"_scbtoken";
        String accessToken=redisUtilsService.getKey(key);

        HttpHeaders params =new HttpHeaders();
        params.add("applicationKey",SCBConfig.apikey);
        params.add("applicationSecret",SCBConfig.apisecret);
        params.add("refreshToken",accessToken);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("accept-language", "EN");
        headers.add("requestUId",String.valueOf(loginDto.getTid()));
        headers.add("resourceOwnerId",token);

        HttpEntity<MultiValueMap> requestEntity = new HttpEntity<MultiValueMap>(params, headers);
        RestTemplate restTemplate=new RestTemplate();
        ResponseEntity<String> response =null;
        try {
            response = restTemplate.exchange(SCBConfig.url+SCBConfig.refresh, HttpMethod.POST, requestEntity, String.class);
        }catch (Exception e){
            throw new ThrowJsonException("Token refresh error");
        }
        String result = response.getBody();
        if(StringUtils.isEmpty(result)){
            throw new ThrowJsonException("SCB AUTHORIZE REQUEST ERROR");
        }

        JSONObject jsonObject=JSONObject.parseObject(result);
        JSONObject statusObj=jsonObject.getJSONObject("status");
        if(!"1000".equals(statusObj.getString("code"))){
            throw new ThrowJsonException(statusObj.getString("description"));
        }
        JSONObject dataObj=jsonObject.getJSONObject("data");
        accessToken =dataObj.getString("accessToken");
        redisUtilsService.setKey(key,accessToken);
        return accessToken;
    }

    /**
     * 生成交易签名
     * @param token 用户token
     * @param money 金额
     * @param paycode 订单号
     * @return
     */
    public JSONObject transactions(String token, BigDecimal money,String paycode){
        //获取用户信息
        LoginDto loginDto=loginTokenService.validLoginLoginDto(token);
        //获取token如果不存在的情况下就重新获取
        String key=loginDto.getTid()+"_scbtoken";
        String accessToken=redisUtilsService.getKey(key);

        JSONObject params =new JSONObject();
        params.put("transactionType","PURCHASE");

        JSONArray transactionSubTypeArr=new JSONArray();
        transactionSubTypeArr.add("BP");
        transactionSubTypeArr.add("CCFA");
        params.put("transactionSubType",transactionSubTypeArr);

        params.put("sessionValidityPeriod",60);

        JSONObject billPaymentObj=new JSONObject();
        billPaymentObj.put("paymentAmount",money);
        billPaymentObj.put("accountTo",SCBConfig.account_id);
        billPaymentObj.put("ref1",paycode);
        billPaymentObj.put("ref2",paycode);
        billPaymentObj.put("ref3","QLC");

        params.put("billPayment",billPaymentObj);

        JSONObject creditCardFullAmountObj=new JSONObject();
        creditCardFullAmountObj.put("merchantId",SCBConfig.merchant_id);
        creditCardFullAmountObj.put("terminalId",SCBConfig.terminal_id);
        creditCardFullAmountObj.put("orderReference",paycode);
        creditCardFullAmountObj.put("paymentAmount",money);

        params.put("creditCardFullAmount",creditCardFullAmountObj);

        JSONObject merchantMetaDataObj=new JSONObject();
        merchantMetaDataObj.put("callbackUrl",SCBConfig.app_name+"://payment-result");
        merchantMetaDataObj.put("extraData",new JSONObject());


        JSONObject paymentObj=new JSONObject();
        paymentObj.put("type","TEXT");
        paymentObj.put("title","Power Plus");
        paymentObj.put("header","");
        paymentObj.put("description","Pay");
        JSONArray paymentArr=new JSONArray();
        paymentArr.add(paymentObj);
        merchantMetaDataObj.put("paymentInfo",paymentArr);

        params.put("merchantMetaData",merchantMetaDataObj);


        Map<String,String> headers =new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("authorization","Bearer "+accessToken);
        headers.put("accept-language", "EN");
        headers.put("resourceOwnerId",token);
        headers.put("requestUId",String.valueOf(loginDto.getTid()));
        headers.put("channel","scbeasy");

        String result="";
        try {
            result= SCBHttpsUtils.httpPostRaw(SCBConfig.url+SCBConfig.transactions,params.toJSONString(),headers);
        }catch (Exception e){
            e.printStackTrace();
        }
        if(StringUtils.isEmpty(result)){
            throw new ThrowJsonException("SCB AUTHORIZE REQUEST ERROR");
        }

        JSONObject jsonObject=JSONObject.parseObject(result);
        JSONObject statusObj=jsonObject.getJSONObject("status");
        if(!"1000".equals(statusObj.getString("code"))){
            throw new ThrowJsonException(statusObj.getString("description"));
        }

        JSONObject dataObj=jsonObject.getJSONObject("data");
        String deeplinkUrl=dataObj.getString("deeplinkUrl");
        //回调到网页
//        deeplinkUrl=deeplinkUrl+"?callback_url=https://192.168.0.189:8002/pay/validAuthorize";
        dataObj.put("deeplinkUrl",deeplinkUrl);
        System.out.println(dataObj);
        return dataObj;
    }

}
