# syntax=docker/dockerfile:1

FROM node:24-alpine AS build
# The root prepare script installs git hooks, which a build image has no use for.
ENV HUSKY=0
RUN corepack enable
WORKDIR /build
# Manifests first: dependencies change far less often than sources, so they get their own layer.
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml .npmrc tsconfig.base.json ./
COPY packages/shared/package.json packages/shared/
COPY frontend/package.json frontend/
RUN pnpm install --frozen-lockfile --filter @technical-test-davivienda/frontend...
COPY packages/shared/ packages/shared/
COPY frontend/ frontend/
RUN pnpm --filter @technical-test-davivienda/frontend build

FROM nginx:1.30-alpine AS runtime
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/frontend/dist /usr/share/nginx/html
EXPOSE 80
