# ---- Étape 1 : build Angular ----
FROM node:22-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npx ng build --configuration production

# ---- Étape 2 : nginx sert le site statique et relaie /api vers l'API ----
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/web/browser /usr/share/nginx/html
EXPOSE 80
