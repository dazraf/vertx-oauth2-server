# OAuth2-Server

This is purely for experimental purposes. Not fit for use, yet.

This is an in-memory OAuth2 compliant service implemented as a vert.x verticle.
To start it, deploy [`OAuth2ServerVerticle`](src/main/java/io/dazraf/oauth2/OAuth2ServerVerticle).

## Config
The configuration has the following fields and defaults:

```json
{
  "port": 8080,

  "basePath": "/oauth2",
  "apiPath": "/api",
  "loginURL": "/oauth2/login.html",

  "clients": {
    "acme1": {
      "name": "Acme Industries Inc.",
      "secret": "secret"
    }
  },

  "scopes": {
    "fp": {
      "description": "Access to Faster Payment"
    },
    "loyalty-read": {
      "description": "Access to read your Loyalty balance"
    }
  },

  "users": {
    "john": {
      "password": "john"
    },
    "james": {
      "password": "james"
    }
    //... etc
  }
}
```

## OAuth2 end points

### OAuth2 Final Spec

[https://localhost:$port/$baseURL/$apiPath/authorize](https://localhost:$port/$baseURL/$apiPath/authorize)
[https://localhost:$port/$baseURL/$apiPath/token](https://localhost:$port/$baseURL/$apiPath/token)

### Non Standard (as none exists)

[https://localhost:$port/$baseURL/$apiPath/tokeninfo](https://localhost:$port/$baseURL/$apiPath/tokeninfo)

### Private

[https://localhost:$port/$baseURL/$api/reset](https://localhost:$port/$baseURL/$api/reset)

## Security: TLS/SSL and JKS

Configured as per [these instructions](https://www.sslshopper.com/article-how-to-create-a-self-signed-certificate-using-java-keytool.html)
