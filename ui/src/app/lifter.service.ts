import {Injectable} from '@angular/core';
import {Observable} from 'rxjs/Observable';
import {HttpClient, HttpHeaders} from "@angular/common/http";
import 'rxjs/Rx';
import {environment} from '../environments/environment';

@Injectable()
export class LifterService {

  config: string;

  constructor(private http: HttpClient) {
  }

  getConfiguration(): Observable<string> {
    return this.http
      .get(environment.api('config'),
        {
          headers: new HttpHeaders().set('Accept', 'application/x-yaml'),
          responseType: 'text'
        }).map((body) => {
        this.config = body;
        return body;
      });
  }

  setConfiguration(config: string): Observable<any> {
    if (this.config !== config) {
      return this.http.post(environment.api('config'), config);
    }
    return Observable.from([]);
  }
}
