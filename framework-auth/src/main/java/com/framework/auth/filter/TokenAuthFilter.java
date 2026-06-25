package com.framework.auth.filter;

import com.framework.auth.context.LoginUser;
import com.framework.auth.context.UserContextHolder;
import com.framework.auth.jwt.JwtUtils;
import com.framework.auth.service.SessionManager;
import com.framework.core.constant.FrameworkConstants;
import com.framework.core.result.Result;
import com.framework.core.result.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Token 认证过滤器
 */
@Slf4j
public class TokenAuthFilter extends OncePerRequestFilter {

    private final SessionManager sessionManager;
    private final Set<String> whiteList;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TokenAuthFilter(JwtUtils jwtUtils, SessionManager sessionManager, Set<String> whiteList) {
        this(sessionManager, whiteList);
    }

    public TokenAuthFilter(SessionManager sessionManager, Set<String> whiteList) {
        this.sessionManager = sessionManager;
        this.whiteList = whiteList;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        UserContextHolder.clear();
        try {
            String uri = request.getRequestURI();

            // 白名单放行
            if (isWhiteListed(uri)) {
                chain.doFilter(request, response);
                return;
            }

            // 提取 Token
            String authHeader = request.getHeader(FrameworkConstants.AUTH_HEADER);
            if (authHeader == null || !authHeader.startsWith(FrameworkConstants.TOKEN_PREFIX)) {
                writeUnauthorized(response, "请先登录");
                return;
            }
            String token = authHeader.substring(FrameworkConstants.TOKEN_PREFIX.length()).trim();
            if (token.isEmpty()) {
                writeUnauthorized(response, "请先登录");
                return;
            }

            // 校验 Token
            if (!sessionManager.validateAccessToken(token)) {
                writeUnauthorized(response, "Token无效或已过期");
                return;
            }

            // 注入登录用户
            LoginUser user = sessionManager.getLoginUser(token);
            if (user == null) {
                writeUnauthorized(response, "Token无效或已过期");
                return;
            }
            UserContextHolder.set(user);

            chain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();
        }
    }

    private boolean isWhiteListed(String uri) {
        if (whiteList == null || whiteList.isEmpty()) {
            return false;
        }
        return whiteList.stream().anyMatch(pattern -> pathMatcher.match(pattern, uri));
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ResultCode.UNAUTHORIZED.getCode(), message)));
    }
}
