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
4. Use **Add model manually** for e.g. `gemini-3.8-flash`.

## Behavior

- Uses Vertex AI's project-scoped `generateContent` and
  `streamGenerateContent?alt=sse` endpoints.
- Shares Gemini request JSON, streaming response, text/thought and image handling
  with the existing Google protocol.
- Does **not** attempt Model Garden listPublisherModels: that API requires OAuth
  and returns a distinct `publisherModels[]` schema.
- Does **not** add Google OAuth2 access-token refresh. Use the bound API key
  only with endpoints that support it.

**Security:** Keys stored in Android applications are at risk of extraction;
for public releases prefer a backend proxy and managed Google Cloud credentials.
Cloud credits and available model access must be verified separately.
