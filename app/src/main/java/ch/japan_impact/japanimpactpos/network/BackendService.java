package ch.japan_impact.japanimpactpos.network;

import android.content.Context;
import android.util.Log;
import ch.japan_impact.japanimpactpos.data.pos.CheckedOutItem;
import ch.japan_impact.japanimpactpos.data.pos.PosConfigResponse;
import ch.japan_impact.japanimpactpos.data.pos.PosConfigurationList;
import ch.japan_impact.japanimpactpos.data.pos.PosOrderResponse;
import ch.japan_impact.japanimpactpos.network.exceptions.*;
import com.android.volley.*;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.JsonRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.json.JSONException;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Consumer;

/**
 * @author Louis Vialar
 */
@Singleton
public class BackendService {
    private static final String TAG = "Backend";
    private static final String PREFERENCE_KEY = "saved_login_tokens";
    private static final String API_URL = "https://shop.japan-impact.ch/api";

    private final RequestQueue queue;
    private final Context ctx;
    private final AuthService service;
    private final TokenStorage storage;

    @Inject
    public BackendService(Context ctx, AuthService service) {
        this.queue = Volley.newRequestQueue(ctx.getApplicationContext());
        this.service = service;
        this.ctx = ctx;
        this.storage = new TokenStorage(ctx.getSharedPreferences(PREFERENCE_KEY, Context.MODE_PRIVATE));
    }

    public TokenStorage getStorage() {
        return storage;
    }

    public void login(String email, String password, Consumer<Optional<String>> callback) throws JSONException {
        this.service.login(email, password, token -> {
            Log.i(TAG, "Got ticket " + token);

            JsonRequest<JSONObject> request = new JsonRequest<JSONObject>(
                    Request.Method.POST,
                    API_URL + "/users/login",
                    token,
                    response -> {
                        if (response.has("idToken") && response.has("refreshToken")) {
                            try {
                                storage.setTokens(response.getString("refreshToken"), response.getString("idToken"));
                                callback.accept(Optional.empty());
                            } catch (JSONException e) {
                                e.printStackTrace();
                                callback.accept(Optional.of(AuthService.ErrorCodes.UNKNOWN_ERROR.message));
                            }
                        } else callback.accept(Optional.of(AuthService.ErrorCodes.UNKNOWN_ERROR.message));
                    },
                    error -> {
                        if (error instanceof NetworkError) {
                            callback.accept(Optional.of(AuthService.ErrorCodes.NETWORK_ERROR.message));
                        } else if (error instanceof ServerError) {
                            //Indicates that the server responded with a error response
                            try {
                                String json = new String(error.networkResponse.data, HttpHeaderParser.parseCharset(error.networkResponse.headers, "utf-8"));
                                Log.e(TAG, json);
                            } catch (UnsupportedEncodingException e) {
                                Log.e(TAG, "Encoding error", e);
                            }
                            callback.accept(Optional.of(AuthService.ErrorCodes.UNKNOWN_ERROR.message));
                        } else {
                            Log.e(TAG, "VolleyError. " + error.toString() + " -- " + error.networkResponse, error.getCause());
                            callback.accept(Optional.of(AuthService.ErrorCodes.UNKNOWN_ERROR.message));
                        }
                    }) {
                @Override
                protected Response<JSONObject> parseNetworkResponse(NetworkResponse response) {
                    try {
                        String jsonString =
                                new String(
                                        response.data,
                                        HttpHeaderParser.parseCharset(response.headers, PROTOCOL_CHARSET));
                        return Response.success(
                                new JSONObject(jsonString), HttpHeaderParser.parseCacheHeaders(response));
                    } catch (UnsupportedEncodingException | JSONException e) {
                        return Response.error(new ParseError(e));
                    }
                }

                @Override
                public String getBodyContentType() {
                    return "text/plain";
                }
            };
            Log.i(TAG, "Sending " + new String(request.getBody()));
            queue.add(request);
        }, error -> callback.accept(Optional.of(error.message)));
    }

    private void refreshIdToken(ApiCallback<Void> callback) {
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                API_URL + "/users/refresh",
                null,
                response -> {
                    if (response.has("idToken") && response.has("refreshToken")) {
                        try {
                            storage.setTokens(response.getString("refreshToken"), response.getString("idToken"));
                            callback.onSuccess(null);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            callback.failure(e.getMessage());
                        }
                    } else callback.failure("Missing data");
                }, callback::failure) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>(super.getHeaders());
                if (!storage.isLoggedIn()) {
                    throw new AuthFailureError("Vous devez être connecté pour faire cela.");
                }
                headers.put("authorization", "Refresh " + storage.getRefreshToken());
                return headers;
            }
        };

        queue.add(req);
    }

    public void getConfigs(ApiCallback<List<PosConfigurationList>> callback) {
        TypeToken<List<PosConfigurationList>> tt = new TypeToken<List<PosConfigurationList>>() {
        };
        sendAuthenticatedRequest(tt.getType(), Request.Method.GET, API_URL + "/pos/configurations", callback);
    }

    public void getConfig(int eventId, int id, ApiCallback<PosConfigResponse> callback) {
        sendAuthenticatedRequest(PosConfigResponse.class, Request.Method.GET, API_URL + "/pos/configurations/" + eventId + "/" + id, callback);
    }

    public void placeOrder(Collection<CheckedOutItem> content, ApiCallback<PosOrderResponse> callback) {
        sendAuthenticatedRequest(PosOrderResponse.class, Request.Method.POST, API_URL + "/checkout", new Gson().toJson(content), callback);
    }

    private <T> void sendAuthenticatedRequest(Type clazz, int method, String url, String body, ApiCallback<T> listener) {
        sendAuthenticatedRequest(clazz, method, url, body, listener, true);
    }

    private <T> void sendAuthenticatedRequest(Type clazz, int method, String url, ApiCallback<T> listener) {
        sendAuthenticatedRequest(clazz, method, url, null, listener);
    }

    private <T> void sendAuthenticatedRequest(Type clazz, int method, String url, String body, ApiCallback<T> listener, boolean retry) {
        if (!storage.isLoggedIn()) {
            listener.onFailure(new LoginRequiredException());
            return;
        }

        JavaObjectRequest<T> request = new JavaObjectRequest<>(method, url, body, listener::onSuccess, error -> {
            if (error instanceof AuthFailureError && retry) {
                Log.i(TAG, "Trying to refresh token...");
                this.refreshIdToken(new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) {
                        sendAuthenticatedRequest(clazz, method, url, body, listener, false);
                    }

                    @Override
                    public void onFailure(NetworkException ignored) {
                        ignored.printStackTrace();
                        listener.failure(error);
                    }
                });
            } else {
                listener.failure(error); // Pass the initial error
            }
        }, clazz);

        request.setAuthToken(storage.getIdToken());
        queue.add(request);
    }

    public interface ApiCallback<T> {
        default void failure(String error) {
            onFailure(new GenericNetworkException(error));
        }

        default void failure(VolleyError error) {
            if (error.networkResponse != null) {
                try {
                    if (error.networkResponse.statusCode == 401 || error.networkResponse.statusCode == 403) {
                        onFailure(new AuthorizationError(error));
                    } else onFailure(new ApiException(error));
                } catch (NullPointerException | UnsupportedEncodingException | JSONException e) {
                    e.printStackTrace();
                    onFailure(new VolleyException(error));
                }
            } else {
                onFailure(new VolleyException(error));
            }
        }

        void onSuccess(T data);

        void onFailure(NetworkException error);

    }
}
