package com.proyecto.api.infrastructure.adapter.in.http

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.headers.`Content-Type`
import org.http4s.MediaType
import sttp.tapir.AnyEndpoint
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.apispec.openapi.circe.*
import io.circe.syntax.*

/**
 * Genera y sirve la documentación de la API usando Scalar en lugar de Swagger UI.
 */
object ScalarDocsRoutes:

  def routes(endpoints: List[AnyEndpoint], title: String, version: String): HttpRoutes[IO] =
    val openApiJson = OpenAPIDocsInterpreter()
      .toOpenAPI(endpoints, title, version)
      .asJson
      .spaces2

    HttpRoutes.of[IO] {
      case GET -> Root / "openapi.json" =>
        Ok(openApiJson, `Content-Type`(MediaType.application.json))
      case req if req.method == GET && (req.pathInfo.renderString == "/docs" || req.pathInfo.renderString == "/docs/") =>
        Ok(scalarHtml(title), `Content-Type`(MediaType.text.html))
    }

  private def scalarHtml(title: String): String =
    s"""<!DOCTYPE html>
       |<html>
       |<head>
       |  <title>$title - API Reference</title>
       |  <meta charset="utf-8" />
       |  <meta name="viewport" content="width=device-width, initial-scale=1" />
       |</head>
       |<body>
       |  <script id="api-reference" data-url="/openapi.json" data-configuration='{"persistAuth": true}'></script>
       |  <script src="https://cdn.jsdelivr.net/npm/@scalar/api-reference"></script>
       |  <script>
       |    // 1. Restaurar token guardado si existe
       |    window.latestToken = localStorage.getItem('custom_last_bearer_token') || '';
       |
       |    // 2. Interceptar fetch (Scalar usa fetch para las peticiones)
       |    const originalFetch = window.fetch;
       |    window.fetch = async function(...args) {
       |      const response = await originalFetch.apply(this, args);
       |      try {
       |        const url = args[0] || '';
       |        if (typeof url === 'string' && url.includes('/auth/login')) {
       |          const clonedResponse = response.clone();
       |          const data = await clonedResponse.json();
       |          if (data && data.token) {
       |            console.log('🔑 Antigravity: Token de login capturado automáticamente:', data.token);
       |            window.latestToken = data.token;
       |            localStorage.setItem('custom_last_bearer_token', data.token);
       |          }
       |        }
       |      } catch (err) {
       |        console.error('Error al interceptar fetch en login:', err);
       |      }
       |      return response;
       |    };
       |
       |    // 3. Interceptar XMLHttpRequest por compatibilidad
       |    const originalOpen = XMLHttpRequest.prototype.open;
       |    XMLHttpRequest.prototype.open = function(method, url, ...rest) {
       |      this._url = url;
       |      return originalOpen.apply(this, [method, url, ...rest]);
       |    };
       |
       |    const originalSend = XMLHttpRequest.prototype.send;
       |    XMLHttpRequest.prototype.send = function(...args) {
       |      this.addEventListener('load', function() {
       |        try {
       |          if (this._url && this._url.includes('/auth/login') && this.status >= 200 && this.status < 300) {
       |            const data = JSON.parse(this.responseText);
       |            if (data && data.token) {
       |              console.log('🔑 Antigravity: Token de login capturado automáticamente (XHR):', data.token);
       |              window.latestToken = data.token;
       |              localStorage.setItem('custom_last_bearer_token', data.token);
       |            }
       |          }
       |        } catch (err) {
       |          console.error('Error al interceptar XHR en login:', err);
       |        }
       |      });
       |      return originalSend.apply(this, args);
       |    };
       |
       |    // 4. Inyección en tiempo real en la UI de Scalar (cada 500ms)
       |    setInterval(() => {
       |      if (!window.latestToken) return;
       |
       |      const inputs = Array.from(document.querySelectorAll('input'));
       |      for (const input of inputs) {
       |        if (input.type !== 'text' && input.type !== 'password') continue;
       |        if (input.value === window.latestToken) continue;
       |
       |        let isBearerInput = false;
       |        const placeholder = (input.placeholder || '').toLowerCase();
       |
       |        if (placeholder === 'token' || placeholder.includes('bearer')) {
       |          isBearerInput = true;
       |        } else {
       |          let parent = input.parentElement;
       |          for (let i = 0; i < 4 && parent; i++) {
       |            const text = parent.textContent || '';
       |            if (text.includes('Bearer Token') || text.includes('Bearer')) {
       |              isBearerInput = true;
       |              break;
       |            }
       |            parent = parent.parentElement;
       |          }
       |        }
       |
       |        if (isBearerInput) {
       |          input.value = window.latestToken;
       |          // Forzar la actualización del estado interno de Vue/React
       |          input.dispatchEvent(new Event('input', { bubbles: true }));
       |          input.dispatchEvent(new Event('change', { bubbles: true }));
       |          console.log('✨ Antigravity: Bearer Token autocompletado en Scalar UI!');
       |        }
       |      }
       |    }, 500);
       |  </script>
       |</body>
       |</html>""".stripMargin
