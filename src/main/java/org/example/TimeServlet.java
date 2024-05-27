package org.example;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.WebApplicationTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@WebServlet(value="/time")
public class TimeServlet extends HttpServlet {
    private transient TemplateEngine engine;
    private static final String LAST_TIME_ZONE = "lastTimezone";
    public static final Logger log = LoggerFactory.getLogger(TimeServlet.class);
    @Override
    public void init() throws ServletException {
        engine = new TemplateEngine();
        JakartaServletWebApplication jwApp = JakartaServletWebApplication.buildApplication(this.getServletContext());
        WebApplicationTemplateResolver resolver = new WebApplicationTemplateResolver(jwApp);
        resolver.setPrefix("/WEB-INF/temp/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML5");
        resolver.setOrder(engine.getTemplateResolvers().size());
        resolver.setCacheable(false);
        engine.addTemplateResolver(resolver);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html");
        /** Читаемо кукі */
        Cookie[] cookies = req.getCookies();
        AtomicReference<String> lastTimezoneValue = new AtomicReference<>("");
        if (Objects.nonNull(cookies) && cookies.length>0) {
            Arrays.stream(cookies).forEach(c -> {
                int i = 1;
                if (c.getName().equals(LAST_TIME_ZONE)) {
                    lastTimezoneValue.set(c.getValue());
                }
                log.info("Cookies.{} {} = {}",i,c.getName(), c.getValue());
                i++;
            });
        }
        String dts = getParameter(req, resp,"timezone");
        Map<String, String> params = new LinkedHashMap<>();
        if (req.getParameterMap().size()==0 || dts.length()==0) {
            params.put("You can pass a parameter in the address line like this:","/time?timezone=UTC+2");
        }
        if (dts.length()==0 && lastTimezoneValue.get() != null) {
            dts = updateDateTimeForTimeZone(lastTimezoneValue.get(), resp);
        }  else if (lastTimezoneValue.get() == null) {
            OffsetDateTime now = OffsetDateTime.now( ZoneOffset.UTC );
            dts = now.toString().replace("T", " ").substring(0,19);
        }
        params.put("Current DateTime",dts);
        log.info(dts);
        Context data = new Context(req.getLocale(), Map.of("queryParams", params));
        PrintWriter printWriter = resp.getWriter();
        try {
            engine.process("zonetime", data, printWriter);
        } finally {
            printWriter.close();
        }
    }
    private static String getParameter(HttpServletRequest req, HttpServletResponse resp, String paramName) {
        Map<String, String[]> parameterMap = req.getParameterMap();
        String paramValue = "";
        if (parameterMap.size()>0) {
            paramValue = req.getParameterMap().get(paramName)[0];
            if (paramValue.length() > 1) {
                String tz = null; // %27UTC+2%27
                try {
                    tz = URLEncoder.encode(paramValue, "UTF-8").replace("%27","");
                } catch (UnsupportedEncodingException e) {
                    return paramValue;
                }
                paramValue = updateDateTimeForTimeZone(tz, resp);
            }
        }
        return paramValue;
    }
    private static String updateDateTimeForTimeZone(String tZone, HttpServletResponse resp) {
        try {
            ZoneId zoneId = ZoneId.of(tZone);
            resp.addCookie(new Cookie(LAST_TIME_ZONE, zoneId.getId()));
            DateTimeFormatter zDTFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
            ZonedDateTime zonedDateTime = LocalDateTime.now()
                    .atZone(ZoneId.systemDefault())
                    .withZoneSameInstant(zoneId);

            return zonedDateTime.format(zDTFormatter);
        } catch(DateTimeException e) {
            return "Invalid time zone! Exception: "+e.getMessage();
        }
    }

}
