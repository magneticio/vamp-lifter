import {Injectable} from '@angular/core';
import {Observable} from 'rxjs/Observable';
import {HttpClient, HttpHeaders, HttpParams} from "@angular/common/http";
import 'rxjs/Rx';
import {environment} from '../environments/environment';

@Injectable()
export class LifterService {

  config: string;

  constructor(private http: HttpClient) {
  }

  getConfiguration(base: Boolean, kv: Boolean): Observable<string> {
    const headers = new HttpHeaders().set('Accept', 'application/x-yaml');
    let params = new HttpParams();
    params = kv ? params.append('key_value', 'true') : params;
    params = base ? params.append('static', 'true') : params;

    return this.http
      .get(environment.api('config'),
        {headers: headers, params: params, responseType: 'text'}
      ).map((body) => {
        this.config = body;
        return body;
      });
  }

  setConfiguration(config: string, kv: boolean = false, force: boolean = false): Observable<any> {
    if (force || this.config !== config) {
      let params = new HttpParams();
      params = kv ? params.append('key_value', 'true') : params;
      return this.http.post(environment.api('config'), config, {params: params});
    }
    return Observable.from([]);
  }
}
