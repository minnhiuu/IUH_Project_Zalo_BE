package com.bondhub.messageservice.service.call;

import com.bondhub.messageservice.dto.request.CallRequest;
import com.bondhub.messageservice.dto.response.CallResponse;

public interface CallService {

    CallResponse initiateCall(CallRequest request);

    CallResponse acceptCall(String sessionId);

    void rejectCall(String sessionId);

    void endCall(String sessionId);

    void cancelCall(String sessionId);

    CallResponse getCallToken(String sessionId);
}
