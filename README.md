# OAuth2-Server

This is an in-memory OAuth2 compliant service implemented as a vert.x verticle.
To start it, deploy `OAuth2ServerVerticle`.

## Config
The configuration object has the following fields and defaults:

* `host` - the interface to be bound to. Default: `localhost`
* `port` - the port for the service. Default: `8080`
* `baseURL` - base path for the service. Default: `/oauth2`
* `loginURL` - page to be displayed for login. Default: `$baseURL/login`. The login should be a **POST** to `$baseURL/api/login` 
* `users` - an array of objects of the form: 

```json
{ 
  "username": "<username", 
  "password", "<password>" 
}
```

## OAuth2 end points

`$baseURL/api/authorize`
`$baseURL/api/token`