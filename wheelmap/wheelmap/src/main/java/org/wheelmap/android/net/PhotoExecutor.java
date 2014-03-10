package org.wheelmap.android.net;

import org.wheelmap.android.mapping.node.SingleNode;
import org.wheelmap.android.model.Extra;
import org.wheelmap.android.model.PrepareDatabaseHelper;
import org.wheelmap.android.net.request.AcceptType;
import org.wheelmap.android.net.request.NodeRequestBuilder;
import org.wheelmap.android.net.request.PhotoRequestBuilder;
import org.wheelmap.android.service.RestServiceException;

import android.accounts.NetworkErrorException;
import android.content.Context;
import android.os.Bundle;

/**
 * Created by SMF on 10/03/14.
 */
public class PhotoExecutor extends SinglePageExecutor<SingleNode> implements
        IExecutor{

    private String mWMId = Extra.WM_ID_UNKNOWN;

    public PhotoExecutor(Context context, Bundle bundle) {
        super(context, bundle, SingleNode.class);
    }

    @Override
    public void prepareContent() {
        mWMId = getBundle().getString(Extra.WM_ID);
    }

    @Override
    public void execute() throws RestServiceException {
        PhotoRequestBuilder requestBuilder = null;
        if (mWMId == Extra.WM_ID_UNKNOWN) {
            processException(RestServiceException.ERROR_INTERNAL_ERROR,
                    new IllegalArgumentException(), true);
        }

        requestBuilder = new PhotoRequestBuilder(getServer(), getApiKey(),
                AcceptType.JSON, mWMId);
        int count = executeSingleRequest(requestBuilder);
        if (count == 0) {
            processException(
                    RestServiceException.ERROR_NETWORK_FAILURE,
                    new NetworkErrorException(), true);
        }
    }

    @Override
    public void prepareDatabase() throws RestServiceException {
        PrepareDatabaseHelper.insert(getResolver(), getTempStore().get(0));
        PrepareDatabaseHelper.replayChangedCopies(getResolver());
    }
}