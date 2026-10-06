FROM node:20-alpine
WORKDIR /app
COPY relay.js live.js ./
ENV PORT=8080 DATA_DIR=/data
RUN mkdir -p /data && chown node:node /data
VOLUME /data
EXPOSE 8080
USER node
CMD ["node", "relay.js"]
