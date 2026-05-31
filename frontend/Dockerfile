FROM node:20-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine AS runtime
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
