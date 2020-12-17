package org.sunbird.user.actors;

import java.util.*;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.sunbird.actor.core.BaseActor;
import org.sunbird.actor.router.ActorConfig;
import org.sunbird.common.exception.ProjectCommonException;
import org.sunbird.common.models.response.Response;
import org.sunbird.common.models.util.JsonKey;
import org.sunbird.common.models.util.TelemetryEnvKey;
import org.sunbird.common.request.Request;
import org.sunbird.common.request.RequestContext;
import org.sunbird.common.responsecode.ResponseCode;
import org.sunbird.learner.util.Util;
import org.sunbird.models.user.UserDeclareEntity;
import org.sunbird.telemetry.util.TelemetryUtil;
import org.sunbird.user.service.UserSelfDeclarationService;
import org.sunbird.user.service.impl.UserSelfDeclarationServiceImpl;
import org.sunbird.user.util.UserUtil;

@ActorConfig(
  tasks = {"upsertUserSelfDeclarations"},
  asyncTasks = {"upsertUserSelfDeclarations", "updateUserSelfDeclarationsErrorType"}
)
public class UserSelfDeclarationManagementActor extends BaseActor {
  private UserSelfDeclarationService userSelfDeclarationService =
      UserSelfDeclarationServiceImpl.getInstance();

  @Override
  public void onReceive(Request request) throws Throwable {
    String operation = request.getOperation();
    switch (operation) {
      case "updateUserDeclarations": // update self declare
        updateUserDeclarations(request);
        break;
      case "updateUserSelfDeclarationsErrorType":
        updateUserSelfDeclaredErrorStatus(request);
        break;
      case "upsertUserSelfDeclarations":
        upsertUserSelfDeclaredDetails(request);
        break;
      default:
        onReceiveUnsupportedOperation("UserSelfDeclarationManagementActor");
    }
  }
  /**
   * This method will update self declaration for the user to Cassandra
   *
   * @param actorMessage
   */
  private void updateUserDeclarations(Request actorMessage) {
    logger.info(
        actorMessage.getRequestContext(),
        "UserManagementActor:updateUserDeclarations method called.");

    Util.initializeContext(actorMessage, TelemetryEnvKey.USER);
    String callerId = (String) actorMessage.getContext().get(JsonKey.CALLER_ID);

    actorMessage.toLower();
    Map<String, Object> userMap = actorMessage.getRequest();
    Response response = new Response();
    List<String> errMsgs = new ArrayList<>();

    try {
      List<Map<String, Object>> declarations =
          (List<Map<String, Object>>) userMap.get(JsonKey.DECLARATIONS);
      // Get the User ID
      userMap.put(JsonKey.USER_ID, declarations.get(0).get(JsonKey.USER_ID));

      Map<String, Object> userDbRecord =
          UserUtil.validateExternalIdsAndReturnActiveUser(
              userMap, actorMessage.getRequestContext());

      UserUtil.encryptDeclarationFields(
          declarations, userDbRecord, actorMessage.getRequestContext());

      List<UserDeclareEntity> userDeclareEntityList = new ArrayList<>();
      for (Map<String, Object> declareFieldMap : declarations) {
        UserDeclareEntity userDeclareEntity =
            UserUtil.createUserDeclaredObject(declareFieldMap, callerId);
        userDeclareEntityList.add(userDeclareEntity);
      }

      userMap.remove(JsonKey.DECLARATIONS);
      userMap.put(JsonKey.DECLARATIONS, userDeclareEntityList);

      response =
          userSelfDeclarationService.saveUserSelfDeclareAttributes(
              userMap, actorMessage.getRequestContext());

    } catch (Exception ex) {
      errMsgs.add(ex.getMessage());
      logger.error(
          actorMessage.getRequestContext(),
          "UserSelfDeclarationManagementActor:upsertUserSelfDeclarations: Exception occurred with error message = "
              + ex.getMessage(),
          ex);
    }

    if (CollectionUtils.isNotEmpty((List<String>) response.getResult().get(JsonKey.ERROR_MSG))
        || CollectionUtils.isNotEmpty(errMsgs)) {
      ProjectCommonException.throwServerErrorException(ResponseCode.internalError, errMsgs.get(0));
    }

    sender().tell(response, self());

    Map<String, Object> targetObject = null;
    List<Map<String, Object>> correlatedObject = new ArrayList<>();
    targetObject =
        TelemetryUtil.generateTargetObject(
            (String) userMap.get(JsonKey.USER_ID), TelemetryEnvKey.USER, JsonKey.UPDATE, null);
    TelemetryUtil.telemetryProcessingCall(
        userMap, targetObject, correlatedObject, actorMessage.getContext());
  }

  private void upsertUserSelfDeclaredDetails(Request request) {
    RequestContext context = request.getRequestContext();
    Map<String, Object> requestMap = request.getRequest();

    Response response =
        userSelfDeclarationService.saveUserSelfDeclareAttributes(requestMap, context);

    sender().tell(response, self());
  }

  public void updateUserSelfDeclaredErrorStatus(Request request) {
    Response response = new Response();
    response.put(JsonKey.RESPONSE, JsonKey.SUCCESS);

    Map<String, Object> requestMap = request.getRequest();
    UserDeclareEntity userDeclareEntity = (UserDeclareEntity) requestMap.get(JsonKey.DECLARATIONS);

    if (JsonKey.SELF_DECLARED_ERROR.equals(userDeclareEntity.getStatus())
        && StringUtils.isNotEmpty(userDeclareEntity.getErrorType())) {
      userSelfDeclarationService.updateSelfDeclaration(
          userDeclareEntity, request.getRequestContext());
    } else {
      ProjectCommonException.throwServerErrorException(
          ResponseCode.declaredUserErrorStatusNotUpdated);
    }
    sender().tell(response, self());
  }
}
