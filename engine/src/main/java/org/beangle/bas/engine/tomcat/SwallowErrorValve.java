/*
 * Copyright (C) 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.beangle.bas.engine.tomcat;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.Constants;
import org.apache.catalina.valves.ErrorReportValve;
import org.apache.coyote.ActionCode;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.http.ServerCookie;
import org.apache.tomcat.util.http.ServerCookies;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.Escape;

import java.io.Writer;
import java.util.Collections;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simplify error report
 *
 * @author chaostone
 */
public class SwallowErrorValve extends ErrorReportValve {

  @Override
  protected void report(Request request, Response response, Throwable throwable) {
    int statusCode = response.getStatus();
    if (statusCode < 400 || response.getContentWritten() > 0 || !response.setErrorReported()) {
      return;
    }

    AtomicBoolean result = new AtomicBoolean(false);
    response.getCoyoteResponse().action(ActionCode.IS_IO_ALLOWED, result);
    if (!result.get()) {
      return;
    }

    if (isDevModeEnabled(request)) {
      reportTrace(request, response, throwable);
    } else {
      StringBuilder sb = new StringBuilder();
      sb.append("HTTP Status ").append(statusCode);
      if (null != throwable) sb.append("  Error:").append(throwable.getMessage());
      try {
        Writer writer = response.getReporter();
        if (writer != null) {
          writer.write(sb.toString());
          response.finishResponse();
        }
      } catch (Exception e) {
      }
    }
  }

  private void reportTrace(Request request, Response response, Throwable throwable) {
    int statusCode = response.getStatus();
    String message = Escape.htmlElementContent(response.getMessage());
    if (message == null) {
      if (throwable != null) {
        String exceptionMessage = throwable.getMessage();
        if (exceptionMessage != null && exceptionMessage.length() > 0) {
          message = Escape.htmlElementContent((new Scanner(exceptionMessage)).nextLine());
        }
      }
      if (message == null) {
        message = "";
      }
    }

    String reason = null;
    String description = null;
    StringManager smClient = StringManager.getManager(Constants.Package,
      Collections.enumeration(Collections.singletonList(Locale.ENGLISH)));
    response.setLocale(Locale.ENGLISH);
    try {
      reason = smClient.getString("http." + statusCode + ".reason");
      description = smClient.getString("http." + statusCode + ".desc");
    } catch (Throwable t) {
      ExceptionUtils.handleThrowable(t);
    }
    if (reason == null || description == null) {
      if (message.isEmpty()) {
        return;
      } else {
        reason = smClient.getString("errorReportValve.unknownReason");
        description = smClient.getString("errorReportValve.noDescription");
      }
    }

    StringBuilder sb = new StringBuilder();
    sb.append("<h1>");
    sb.append(smClient.getString("errorReportValve.statusHeader", String.valueOf(statusCode), reason))
      .append("</h1>");
    if (isShowReport()) {
      sb.append("<hr/>");

      if (!message.isEmpty()) {
        sb.append("<p><b>");
        sb.append(smClient.getString("errorReportValve.message"));
        sb.append("</b> ");
        sb.append(message).append("</p>");
      }
      sb.append("<p><b>");
      sb.append(smClient.getString("errorReportValve.description"));
      sb.append("</b> ");
      sb.append(description);
      sb.append("</p>");
      if (throwable != null) {
        String stackTrace = getPartialServletStackTrace(throwable);
        sb.append("<p><b>");
        sb.append(smClient.getString("errorReportValve.exception"));
        sb.append("</b></p><pre>");
        sb.append(Escape.htmlElementContent(stackTrace));
        sb.append("</pre>");

        int loops = 0;
        Throwable rootCause = throwable.getCause();
        while (rootCause != null && (loops < 10)) {
          stackTrace = getPartialServletStackTrace(rootCause);
          sb.append("<p><b>");
          sb.append(smClient.getString("errorReportValve.rootCause"));
          sb.append("</b></p><pre>");
          sb.append(Escape.htmlElementContent(stackTrace));
          sb.append("</pre>");
          rootCause = rootCause.getCause();
          loops++;
        }
      }
      sb.append("<hr/>");
    }
    if (isShowServerInfo()) {
      sb.append("<h3>").append("Beangle Bas").append("</h3>");
    }
    try {
      Writer writer = response.getReporter();
      if (writer != null) {
        writer.write(sb.toString());
        response.finishResponse();
      }
    } catch (Exception e) {
    }

  }

  private static boolean isDevModeEnabled(Request request) {
    ServerCookies cookies = request.getServerCookies();
    for (int i = 0; i < cookies.getCookieCount(); i++) {
      ServerCookie sc = cookies.getCookie(i);
      if (sc.getName().equals("devMode")) {
        return true;
      }
    }
    return false;
  }

}
