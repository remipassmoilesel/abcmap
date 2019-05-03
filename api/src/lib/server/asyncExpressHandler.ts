import {NextFunction, Request, Response} from 'express-serve-static-core';
import express = require('express');

export type AsyncRequestHandler = (req: Request, res: Response, next: NextFunction) => Promise<any>;

export const asyncHandler = (handler: AsyncRequestHandler) =>
    (request: express.Request, response: express.Response, next: express.NextFunction) => {
        handler(request, response, next)
            .then(result => {
                if (result) {
                    response.send(result);
                }
            })
            .catch(err => {
                next(err);
            });
    };