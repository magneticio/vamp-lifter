import {BrowserModule} from '@angular/platform-browser';
import {NgModule} from '@angular/core';
import {RouterModule, Routes} from '@angular/router';
import {BrowserAnimationsModule} from '@angular/platform-browser/animations';
import {
  MdButtonModule, MdDialogModule, MdIconModule, MdListModule, MdProgressBarModule, MdSidenavModule, MdSnackBarModule,
  MdToolbarModule, MdTooltipModule
} from '@angular/material';

import {AppComponent} from './app.component';
import {ConnectionsComponent} from './connections/connections.component';
import {InitializationComponent} from './initialization/initialization.component';
import {ConfigurationComponent, ConfigurationUpdateDialog} from './configuration/configuration.component';
import {AceEditorModule} from 'ng2-ace-editor';
import {LifterService} from './lifter.service';
import {HttpClientModule} from '@angular/common/http';
import {ToolbarComponent} from './toolbar/toolbar.component';
import {ToolbarService} from './toolbar/toolbar.service';

const routes: Routes = [
  {path: 'configuration', component: ConfigurationComponent},
  {path: 'initialization', component: InitializationComponent},
  {path: 'connections', component: ConnectionsComponent},
  {path: '**', redirectTo: 'configuration'}
];

@NgModule({
  declarations: [
    AppComponent,
    ConnectionsComponent,
    InitializationComponent,
    ConfigurationComponent,
    ToolbarComponent,
    ConfigurationUpdateDialog
  ],
  imports: [
    RouterModule.forRoot(routes),
    AceEditorModule,
    HttpClientModule,
    BrowserModule,
    BrowserAnimationsModule,
    MdButtonModule,
    MdIconModule,
    MdToolbarModule,
    MdSidenavModule,
    MdListModule,
    MdProgressBarModule,
    MdTooltipModule,
    MdDialogModule,
    MdSnackBarModule
  ],
  providers: [LifterService, ToolbarService],
  bootstrap: [AppComponent],
  entryComponents: [ConfigurationUpdateDialog]
})
export class AppModule {
}
