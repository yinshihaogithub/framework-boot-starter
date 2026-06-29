package com.framework.auth.filter;

import com.framework.auth.context.LoginUser;
import com.framework.auth.context.UserContextHolder;
import com.framework.auth.jwt.JwtUtils;
import com.framework.auth.service.LoginUserValidator;
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
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Token 认证过滤器
 */
@Slf4j
public class TokenAuthFilter extends OncePerRequestFilter {

    private final SessionManager sessionManager;
    private final Set<String> whiteList;
    private final Collection<LoginUserValidator> loginUserValidators;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TokenAuthFilter(JwtUtils jwtUtils, SessionManager sessionManager, Set<String> whiteList) {
        this(sessionManager, whiteList);
    }

    public TokenAuthFilter(SessionManager sessionManager, Set<String> whiteList) {
        this(sessionManager, whiteList, List.of());
    }

    public TokenAuthFilter(SessionManager sessionManager, Set<String> whiteList,
                           Collection<LoginUserValidator> loginUserValidators) {
        this.sessionManager = sessionManager;
        this.whiteList = whiteList;
        this.loginUserValidators = loginUserValidators == null ? List.of() : loginUserValidators;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        LoginUser previousUser = UserContextHolder.get();
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
            if (!isLoginUserValid(user)) {
                sessionManager.forceLogoutAll(user.getUserId());
                writeUnauthorized(response, "账号已停用或不存在");
                return;
            }
            UserContextHolder.set(user);

            chain.doFilter(request, response);
        } finally {
            UserContextHolder.restore(previousUser);
        }
    }

    private boolean isWhiteListed(String uri) {
        if (whiteList == null || whiteList.isEmpty()) {
            return false;
        }
        return whiteList.stream().anyMatch(pattern -> pathMatcher.match(pattern, uri));
    }

    private boolean isLoginUserValid(LoginUser user) {
        for (LoginUserValidator validator : loginUserValidators) {
            if (!validator.isValid(user)) {
                return false;
            }
        }
        return true;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ResultCode.UNAUTHORIZED.getCode(), message)));
    }
}
