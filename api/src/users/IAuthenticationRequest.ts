import {IUserDto} from './IUserDto';

export interface IAuthenticationRequest {
    username: string;
    password: string;
}

export interface IAuthenticationResult {
    authenticated: boolean;
    user?: IUserDto;
}
