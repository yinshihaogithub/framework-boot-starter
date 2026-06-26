FROM node:22-alpine AS build
WORKDIR /workspace
COPY frontend/admin-web/package*.json ./
RUN npm ci
COPY frontend/admin-web .
RUN npm run build

FROM nginx:1.27-alpine
COPY frontend/admin-web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80
