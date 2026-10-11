interface Logger {
    info(message: string): void;
}

const logger: Logger = {
    info(message: string): void {
        process.stdout.write(message);
    },
};

export function announce(message: string): void {
    logger.info(message);
}
