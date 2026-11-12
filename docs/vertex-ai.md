# Vertex AI protocol (Google Cloud Gemini)

This app provides a separate **Vertex AI** protocol. The original **Google**
protocol still targets Gemini API (AI Studio) at generativelanguage.googleapis.com.

## Configuration

1. Select the **Vertex AI** preset and replace `PROJECT_ID` with the Google Cloud project ID.
2. Use a project-scoped endpoint (global example):

   ```text
   https://aiplatform.googleapis.com/v1/projects/PROJECT_ID/locations/global/publishers/google/models
   ```

   You can also omit the final `/models`, and use an appropriate supported regional
   hostname/location for regional deployments.
3. Enter a service-account-bound Agent Platform API key with sufficient IAM permissions.
   The app authenticates with `x-goog-api-key`.
4. Open **Add model**. To fetch the live Model Garden catalog, enter a short-lived
   Google Cloud OAuth access token in the dedicated field, then tap **Fetch model list**.
   Obtain one with `gcloud auth login` and `gcloud auth print-access-token`.
   A user token normally expires after about 1 hour; renew it if fetching returns 401.
   **Do not use the bound API key in this field.**
   Alternatively use **Add manually** for e.g. `gemini-3.8-flash`.

## Behavior

- Uses Vertex AI's project-scoped `generateContent` and
  `streamGenerateContent?alt=sse` endpoints.
- Shares Gemini request JSON, streaming response, text/thought and image handling
  with the existing Google protocol.
- Model list: calls `GET https://aiplatform.googleapis.com/v1beta1/publishers/google/models`
  with `Authorization: Bearer <OAuth token>` and `x-goog-user-project: <PROJECT_ID>`.
  Parses `publisherModels[]`, follows `nextPageToken`, and filters to Gemini text/chat IDs.
  Catalog access is separate from the service-account-bound API key used for inference.
- The OAuth token is kept **in memory only** while the model picker is open, is
  never saved in provider settings, and is sent only to the fixed Google endpoint.
  The catalog is published metadata: models listed are **not guaranteed** to be
  enabled for your project or location; inference may still return 403/404.
- Automatic Google OAuth sign-in/refresh is not implemented. This feature requires
  a valid OAuth access token pasted by the user whenever a fresh list is needed.
- Does **not** add Google OAuth2 access-token refresh. Use the bound API key
  only with endpoints that support it.

**Security:** Keys stored in Android applications are at risk of extraction;
for public releases prefer a backend proxy and managed Google Cloud credentials.
Cloud credits and available model access must be verified separately.
