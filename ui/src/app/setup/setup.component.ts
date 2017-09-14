import { Component, OnInit } from '@angular/core';
import {ToolbarAction, ToolbarService} from '../toolbar/toolbar.service';
import {HttpClient} from '@angular/common/http';
import {environment} from '../../environments/environment';
import {MdSnackBar, MdSnackBarConfig} from "@angular/material";

@Component({
  selector: 'app-setup',
  templateUrl: './setup.component.html',
  styleUrls: ['./setup.component.scss']
})
export class SetupComponent implements OnInit {

  model: any = {};
  artifacts: Array<string> = [];

  constructor(private http: HttpClient, private toolbar: ToolbarService, private snackBar: MdSnackBar) {
  }

  ngOnInit() {
    this.toolbar.actions.next([
      new ToolbarAction(this, 'play_circle_filled', 'Run', ($this) => $this.run())
    ]);
    this.http.get(environment.api('setup')).subscribe((setup) => {
      this.model = setup || {};
      const artifacts = setup['artifacts'] || {};
      for (let artifact in artifacts) {
        if (artifacts.hasOwnProperty(artifact)) {
          this.artifacts.push(artifact);
        }
      }
    }, () => {
      const config = new MdSnackBarConfig();
      config.duration = 2000;
      this.snackBar.open('Error occurred during data retrieval!', null, config);
    });
  }

  private run() {
    this.toolbar.progressStart();
    this.http.post(environment.api('setup'), this.model).subscribe(
      () => {
        const config = new MdSnackBarConfig();
        config.duration = 2000;
        this.snackBar.open('Everything is setup correctly!', null, config);
        this.toolbar.progressStop();
      },
      () => {
        const config = new MdSnackBarConfig();
        config.duration = 2000;
        this.snackBar.open('An error occurred!', null, config);
        this.toolbar.progressStop();
      },
      () => {
        const config = new MdSnackBarConfig();
        config.duration = 2000;
        this.snackBar.open('Everything is setup correctly!', null, config);
        this.toolbar.progressStop();
      }
    );
  }
}
