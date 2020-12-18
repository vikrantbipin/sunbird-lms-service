package util;

import static util.Common.createResponseParamObj;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.response.ResponseParams;
import org.sunbird.common.models.util.EntryExitLogEvent;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.LoggerUtil;
import org.sunbird.common.models.util.datasecurity.impl.LogMaskServiceImpl;
import org.sunbird.common.responsecode.ResponseCode;

public class PrintEntryExitLog {

  private static LoggerUtil logger = new LoggerUtil(PrintEntryExitLog.class);
  private static LogMaskServiceImpl logMaskService = new LogMaskServiceImpl();
  private static ObjectMapper objectMapper = new ObjectMapper();

  public static void printEntryLog(org.sunbird.common.request.Request request) {
    try {
      EntryExitLogEvent entryLogEvent = new EntryExitLogEvent();
      entryLogEvent.setEid("LOG");
      String url = (String) request.getContext().get(JsonKey.URL);
      String entryLogMsg =
          "ENTRY LOG: method : "
              + request.getContext().get(JsonKey.METHOD)
              + ", url: "
              + url
              + " , For Operation : "
              + request.getOperation();
      String requestId = request.getRequestContext().getReqId();
      List<Map<String, Object>> params = new ArrayList<>();
      Map<String, Object> reqMap = request.getRequest();
      if (url.contains("search") || url.contains("lookup")) {
        Map<String, Object> filters = (Map<String, Object>) reqMap.get(JsonKey.FILTERS);
        if (MapUtils.isNotEmpty(filters)) {
          maskAttributes(filters);
        }
      }
      maskAttributes(reqMap);
      params.add(reqMap);
      entryLogEvent.setEdata("system", "trace", requestId, entryLogMsg, params);
      logger.info(request.getRequestContext(), entryLogEvent.toString());
    } catch (Exception ex) {
      logger.error("Exception occurred while logging entry log", ex);
    }
  }

  public static void printExitLogOnSuccessResponse(
      org.sunbird.common.request.Request request, Response response) {
    try {
      EntryExitLogEvent exitLogEvent = new EntryExitLogEvent();
      exitLogEvent.setEid("LOG");
      String url = (String) request.getContext().get(JsonKey.URL);
      String entryLogMsg =
          "EXIT LOG: method : "
              + request.getContext().get(JsonKey.METHOD)
              + ", url: "
              + url
              + " , For Operation : "
              + request.getOperation();
      String requestId = request.getRequestContext().getReqId();
      List<Map<String, Object>> params = new ArrayList<>();
      if (null != response) {
        if (MapUtils.isNotEmpty(response.getResult())) {
          if (url.equalsIgnoreCase("/private/user/v1/lookup")) {
            if (CollectionUtils.isNotEmpty(
                (List<Map<String, Object>>) response.getResult().get(JsonKey.RESPONSE))) {
              List<Map<String, Object>> resList =
                  (List<Map<String, Object>>) response.getResult().get(JsonKey.RESPONSE);
              params.add(resList.get(0));
            }
          } else {
            Map<String, Object> resMap = response.getResult();
            maskAttributes(resMap);
            params.add(resMap);
          }
        }

        if (null != response.getParams()) {
          Map<String, Object> resParam = new HashMap<>();
          resParam.putAll(objectMapper.convertValue(response.getParams(), Map.class));
          params.add(resParam);
        }
      }
      exitLogEvent.setEdata("system", "trace", requestId, entryLogMsg, params);
      logger.info(request.getRequestContext(), exitLogEvent.toString());
    } catch (Exception ex) {
      logger.error("Exception occurred while logging exit log", ex);
    }
  }

  public static void printExitLogOnFailure(
      org.sunbird.common.request.Request request, ProjectCommonException exception) {
    try {
      EntryExitLogEvent exitLogEvent = new EntryExitLogEvent();
      exitLogEvent.setEid("LOG");
      String entryLogMsg =
          "EXIT LOG: method : "
              + request.getContext().get(JsonKey.METHOD)
              + ", url: "
              + request.getContext().get(JsonKey.URL)
              + " , For Operation : "
              + request.getOperation();
      String requestId = request.getRequestContext().getReqId();
      List<Map<String, Object>> params = new ArrayList<>();
      if (null == exception) {
        exception =
            new ProjectCommonException(
                ResponseCode.internalError.getErrorCode(),
                ResponseCode.internalError.getErrorMessage(),
                ResponseCode.SERVER_ERROR.getResponseCode());
      }

      ResponseCode code = ResponseCode.getResponse(exception.getCode());
      if (code == null) {
        code = ResponseCode.SERVER_ERROR;
      }
      ResponseParams responseParams =
          createResponseParamObj(code, exception.getMessage(), requestId);
      if (responseParams != null) {
        responseParams.setStatus(JsonKey.FAILED);
        if (exception.getCode() != null) {
          responseParams.setStatus(JsonKey.FAILED);
        }
        if (!StringUtils.isBlank(responseParams.getErrmsg())
            && responseParams.getErrmsg().contains("{0}")) {
          responseParams.setErrmsg(exception.getMessage());
        }
      }
      if (null != responseParams) {
        Map<String, Object> resParam = new HashMap<>();
        resParam.putAll(objectMapper.convertValue(responseParams, Map.class));
        params.add(resParam);
      }
      exitLogEvent.setEdata("system", "trace", requestId, entryLogMsg, params);
      logger.info(request.getRequestContext(), exitLogEvent.toString());
    } catch (Exception ex) {
      logger.error("Exception occurred while logging exit log", ex);
    }
  }

  private static String maskId(String value, String type) {
    if (JsonKey.EMAIL.equalsIgnoreCase(type)) {
      return logMaskService.maskEmail(value);
    } else if (JsonKey.PHONE.equalsIgnoreCase(type)) {
      return logMaskService.maskPhone(value);
    } else if (JsonKey.OTP.equalsIgnoreCase(type)) {
      return logMaskService.maskOTP(value);
    }
    return "";
  }

  private static void maskAttributes(Map<String, Object> filters) {
    String phone = (String) filters.get(JsonKey.PHONE);
    if (StringUtils.isNotBlank(phone)) {
      filters.put(JsonKey.PHONE, maskId(phone, JsonKey.PHONE));
    }
    String email = (String) filters.get(JsonKey.EMAIL);
    if (StringUtils.isNotBlank(email)) {
      filters.put(JsonKey.EMAIL, maskId(email, JsonKey.EMAIL));
    }
    String otp = (String) filters.get(JsonKey.OTP);
    if (StringUtils.isNotBlank(otp)) {
      filters.put(JsonKey.OTP, maskId(otp, JsonKey.OTP));
    }
    String password = (String) filters.get(JsonKey.PASSWORD);
    if (StringUtils.isNotBlank(password)) {
      filters.put(JsonKey.PASSWORD, "**********");
    }
  }
}
