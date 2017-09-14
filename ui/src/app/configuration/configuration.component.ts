import {Component, Inject, OnDestroy, OnInit} from '@angular/core';
import {ToolbarAction, ToolbarService} from "../toolbar/toolbar.service";
import {MD_DIALOG_DATA, MdDialog, MdDialogRef, MdSnackBar, MdSnackBarConfig} from "@angular/material";
import {environment} from "../../environments/environment";
import {Observable} from "rxjs/Observable";
import {HttpClient, HttpHeaders, HttpParams} from "@angular/common/http";
import 'rxjs/Rx';

@Component({
  selector: 'app-configuration',
  templateUrl: './configuration.component.html',
  styleUrls: ['./configuration.component.scss'],
})
export class ConfigurationComponent implements OnInit, OnDestroy {

  config: string;
  text: string = '';
  options: any = {maxLines: 1000, printMargin: false};
  footer: string = '';

  constructor(private http: HttpClient,
              private toolbar: ToolbarService,
              private dialog: MdDialog,
              private snackBar: MdSnackBar) {
  }

  ngOnInit() {
    this.getConfiguration(false, false).subscribe((text) => this.text = text, () => {
      this.footer = 'Configuration can\'t be retrieved!';
    });
    this.toolbar.actions.next([
      new ToolbarAction(this, 'autorenew', 'Reload from Configuration', ($this) => $this.refresh()),
      new ToolbarAction(this, 'cloud_download', 'Pull from KV Store', ($this) => $this.pull()),
      new ToolbarAction(this, 'cloud_upload', 'Push to KV Store', ($this) => $this.push())
    ]);
  }

  onChange() {
    this.set(false, false, () => {
      this.footer = 'Configuration has been saved.';
    }, () => {
      this.footer = 'Configuration can\'t be saved - invalid input.';
    });
  }

  ngOnDestroy(): void {
    this.set();
  }

  refresh() {
    this.getConfiguration(true, false).subscribe((text) => {
      if (text !== this.text) {
        let dialogRef = this.dialog.open(ConfigurationUpdateDialog);
        dialogRef.afterClosed().subscribe(result => {
          if (result) {
            this.text = text;
            this.set(false, true);
          }
        });
      }
    });
  }

  pull() {
    this.getConfiguration(false, true).subscribe((text) => {
      if (text !== this.text) {
        let dialogRef = this.dialog.open(ConfigurationUpdateDialog);
        dialogRef.afterClosed().subscribe(result => {
          if (result) {
            this.text = text;
            this.set(true, true);
          }
        });
      }
    }, () => {
      const config = new MdSnackBarConfig();
      config.duration = 2000;
      this.snackBar.open('An error occurred!', null, config);
    });
  }

  push() {
    this.set(true, true, () => {
      const config = new MdSnackBarConfig();
      config.duration = 2000;
      this.snackBar.open('Configuration is successfully stored to KV store!', null, config);
    }, () => {
      const config = new MdSnackBarConfig();
      config.duration = 2000;
      this.snackBar.open('An error occurred!', null, config);
    })
  }

  private set(kv: boolean = false, force: boolean = false, success: () => any = () => {
  }, error: () => any = () => {
  }): void {
    this.toolbar.progressStart();
    this.setConfiguration(this.text, kv, force).subscribe(
      () => {
        success();
        this.toolbar.progressStop();
      },
      () => {
        error();
        this.toolbar.progressStop();
      },
      () => {
        success();
        this.toolbar.progressStop();
      }
    );
  }

  private getConfiguration(base: Boolean, kv: Boolean): Observable<string> {
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

  private setConfiguration(config: string, kv: boolean = false, force: boolean = false): Observable<any> {
    if (force || this.config !== config) {
      let params = new HttpParams();
      params = kv ? params.append('key_value', 'true') : params;
      return this.http.post(environment.api('config'), config, {params: params});
    }
    return Observable.from([]);
  }
}

@Component({
  selector: 'app-configuration-dialog',
  templateUrl: './dialog.component.html',
})
export class ConfigurationUpdateDialog {

  constructor(public dialogRef: MdDialogRef<ConfigurationUpdateDialog>,
              @Inject(MD_DIALOG_DATA) public data: any) {
  }

  onNoClick(): void {
    this.dialogRef.close();
  }
}
